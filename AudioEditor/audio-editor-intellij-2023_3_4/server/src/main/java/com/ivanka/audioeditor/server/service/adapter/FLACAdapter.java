package com.ivanka.audioeditor.server.service.adapter;

import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class FLACAdapter implements IAudioFormatAdapter {

    private final FFmpegAdapter ffmpegAdapter;

    public FLACAdapter(FFmpegAdapter ffmpegAdapter) {
        this.ffmpegAdapter = ffmpegAdapter;
    }

    @Override
    public File convert(File inputFile, String targetFormat) throws Exception {
        System.out.println("Використовується FLACAdapter → FFmpeg");
        return ffmpegAdapter.convert(inputFile, "flac");
    }

    @Override
    public boolean supports(String format) {
        return "flac".equalsIgnoreCase(format);
    }
}
