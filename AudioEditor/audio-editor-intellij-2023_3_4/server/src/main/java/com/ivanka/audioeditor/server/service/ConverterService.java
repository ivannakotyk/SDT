package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.server.service.adapter.IAudioFormatAdapter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class ConverterService {
    private final List<IAudioFormatAdapter> adapters;
    private final FileStorageService storage;
    public ConverterService(
            List<IAudioFormatAdapter> adapters,
            FileStorageService storage
    ) {
        this.adapters = adapters;
        this.storage = storage;
    }

    public File export(File wavFile, String targetFormat, String exportBaseName) throws Exception {
        File converted = null;
        try {
            converted = findAdapterAndConvert(wavFile, targetFormat);

            String finalName = exportBaseName + "." + targetFormat.toLowerCase();
            return storage.saveExportedFile(converted, finalName);

        } finally {
            if (converted != null) {
                storage.deleteTempFile(converted);
            }
        }
    }

    private File findAdapterAndConvert(File inputFile, String format) throws Exception {
        for (IAudioFormatAdapter adapter : adapters) {
            if (adapter.supports(format)) {
                return adapter.convert(inputFile, format);
            }
        }
        throw new UnsupportedOperationException("Format not supported: " + format);
    }
}