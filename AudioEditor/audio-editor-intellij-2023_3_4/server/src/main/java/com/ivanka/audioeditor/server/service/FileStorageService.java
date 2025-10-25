package com.ivanka.audioeditor.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static FileStorageService INSTANCE;

    @Value("${storage.root}")
    private String storageRoot;

    @Value("${storage.uploadsDir}")
    private String uploadsDir;

    @Value("${storage.wavDir}")
    private String wavDir;

    @Value("${storage.exportsDir}")
    private String exportsDir;

    private FileStorageService() {
    }

    public static synchronized FileStorageService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FileStorageService();
        }
        return INSTANCE;
    }

    public File saveTempFile(MultipartFile multipartFile) throws IOException {
        Path uploadPath = Path.of(uploadsDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String uniqueName = "track-" + multipartFile.getOriginalFilename().replaceAll("\\s+", "_")
                + "-" + UUID.randomUUID() + ".wav";

        Path targetFile = uploadPath.resolve(uniqueName);
        multipartFile.transferTo(targetFile);

        System.out.println("Uploaded temp file saved to: " + targetFile.toAbsolutePath());
        return targetFile.toFile();
    }

    public File saveWavFile(File wavFile) throws IOException {
        Path wavPath = Path.of(wavDir);
        if (!Files.exists(wavPath)) {
            Files.createDirectories(wavPath);
        }

        Path target = wavPath.resolve(wavFile.getName());
        Files.copy(wavFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("WAV file saved to: " + target.toAbsolutePath());
        return target.toFile();
    }

    public File saveExportedFile(File sourceFile, String finalName) throws IOException {
        Path exportPath = Path.of(exportsDir);

        if (!Files.exists(exportPath)) {
            Files.createDirectories(exportPath);
        }

        Path targetPath = exportPath.resolve(finalName);
        Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        System.out.printf("Exported file saved at: %s (%d bytes)%n",
                targetPath.toAbsolutePath(), Files.size(targetPath));

        return targetPath.toFile();
    }

    public void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                System.out.println("Temp file deleted: " + file.getName());
            } else {
                System.err.println("Failed to delete temp file: " + file.getAbsolutePath());
            }
        }
    }
}