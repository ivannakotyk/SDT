package com.ivanka.audioeditor.server.service.adapter;

import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class OGGAdapter implements IAudioFormatAdapter {

    private final FFmpegAdapter ffmpegAdapter;

    public OGGAdapter(FFmpegAdapter ffmpegAdapter) {
        this.ffmpegAdapter = ffmpegAdapter;
    }

    @Override
    public File convert(File inputFile, String targetFormat) throws Exception {
        System.out.println("🎧 Використовується OGGAdapter → FFmpeg");
        return ffmpegAdapter.convert(inputFile, "ogg");
    }
}
