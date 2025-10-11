package com.ivanka.audioeditor.server.service.adapter;

import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class WAVAdapter implements IAudioFormatAdapter {

    private final FFmpegAdapter ffmpegAdapter;

    public WAVAdapter(FFmpegAdapter ffmpegAdapter) {
        this.ffmpegAdapter = ffmpegAdapter;
    }

    @Override
    public File convert(File inputFile, String targetFormat) throws Exception {
        System.out.println("🎧 Використовується WAVAdapter → FFmpeg");
        return ffmpegAdapter.convert(inputFile, "wav");
    }
}
