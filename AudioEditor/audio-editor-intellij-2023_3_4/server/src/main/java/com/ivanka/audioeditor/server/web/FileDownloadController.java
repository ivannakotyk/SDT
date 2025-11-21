package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;


@Controller
@RequestMapping("/api")
@CrossOrigin
public class FileDownloadController {

    @Value("${storage.uploadsDir:storage/uploads}")
    private String uploadsDir;

    private final FileStorageService storageService;

    public FileDownloadController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/uploads/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadSourceFile(@PathVariable String filename) {
        try {
            File dir = new File(uploadsDir).getAbsoluteFile();
            File file = new File(dir, filename);

            System.out.println("--- DOWNLOAD REQUEST ---");
            System.out.println("Looking for file: " + filename);
            System.out.println("In directory: " + dir.getAbsolutePath());
            System.out.println("Full path target: " + file.getAbsolutePath());

            if (!file.exists()) {
                System.err.println("FILE NOT FOUND on disk!");
                if (dir.exists()) {
                    System.out.println("Files available in directory:");
                    String[] list = dir.list();
                    if (list != null) {
                        for (String f : list) System.out.println(" - " + f);
                    } else {
                        System.out.println(" (Directory is empty)");
                    }
                } else {
                    System.err.println("Directory does not exist: " + dir.getAbsolutePath());
                }
                return ResponseEntity.notFound().build();
            }

            System.out.println("File found! Size: " + file.length());

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/exports/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadExported(@PathVariable String filename) {
        try {
            File file = storageService.loadExportedFile(filename);
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}