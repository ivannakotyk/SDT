package com.ivanka.audioeditor.server.model;

import jakarta.persistence.*;
import java.io.File;
import java.nio.file.Files;

@Entity
@Table(name = "audio_file")
public class AudioFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath;
    private String fileFormat;
    private Double durationSec;
    private Integer sampleRate;
    private Long fileSizeBytes;

    public AudioFileEntity() {}

    public AudioFileEntity(String fileName, String filePath, String fileFormat) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileFormat = fileFormat;
    }

    public Long getId() { return id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }


    public static AudioFileEntity fromFile(File f, String format) {
        AudioFileEntity entity = new AudioFileEntity();
        entity.setFileName(f.getName());
        entity.setFilePath(f.getAbsolutePath());
        entity.setFileFormat(format);
        entity.setFileSizeBytes(f.exists() ? f.length() : 0L);
        return entity;
    }

    @Override
    public String toString() {
        return "AudioFileEntity{" +
                "id=" + id +
                ", fileName='" + fileName + '\'' +
                ", fileFormat='" + fileFormat + '\'' +
                ", durationSec=" + durationSec +
                ", sampleRate=" + sampleRate +
                '}';
    }
}