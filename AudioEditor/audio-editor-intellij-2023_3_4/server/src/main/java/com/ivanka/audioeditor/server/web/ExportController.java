package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.service.ConverterService;
import com.ivanka.audioeditor.server.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ConverterService converter;
    private final FileStorageService storage;

    public ExportController(ConverterService converter, FileStorageService storage) {
        this.converter = converter;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<?> exportFile(
            @RequestParam("userId") Long userId,
            @RequestParam("projectId") Long projectId,
            @RequestParam("format") String format,
            @RequestParam("trackName") String trackName,
            @RequestParam("file") MultipartFile file
    ) {
        File uploaded = null;
        try {
            uploaded = storage.saveTempFile(file);
            String baseName = trackName.replaceAll("[^a-zA-Z0-9._() -]+", "_");

            File exported = converter.export(uploaded, format, baseName);

            String fileName = exported.getName();
            String urlPath = "/exports/" + fileName;

            Map<String, Object> res = new HashMap<>();
            res.put("fileName", fileName);
            res.put("path", urlPath);
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        } finally {
            if (uploaded != null) {
                storage.deleteTempFile(uploaded);
            }
        }
    }
}