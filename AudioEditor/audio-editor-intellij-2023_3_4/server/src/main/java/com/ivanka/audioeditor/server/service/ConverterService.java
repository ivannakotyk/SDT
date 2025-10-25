package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.server.service.adapter.IAudioFormatAdapter;
import com.ivanka.audioeditor.server.service.adapter.FFmpegAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ConverterService {

    private final IAudioFormatAdapter adapter;
    private final FileStorageService storage;

    public ConverterService(
            @Qualifier("FFmpegAdapter") IAudioFormatAdapter adapter,
            FileStorageService storage
    ) {
        this.adapter = adapter;
        this.storage = storage;
    }

    public File export(File wavFile, String targetFormat, String exportBaseName) throws Exception {
        File converted = adapter.convert(wavFile, targetFormat);
        String finalName = exportBaseName + "." + targetFormat.toLowerCase();
        return storage.saveExportedFile(converted, finalName);
    }
}