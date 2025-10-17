package com.ivanka.audioeditor.server.service.adapter;

import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class MP3Adapter implements IAudioFormatAdapter {

    private final FFmpegAdapter ffmpegAdapter;

    public MP3Adapter(FFmpegAdapter ffmpegAdapter) {
        this.ffmpegAdapter = ffmpegAdapter;
    }

    @Override
    public File convert(File inputFile, String targetFormat) throws Exception {
        System.out.println("Використовується MP3Adapter → FFmpeg");
        return ffmpegAdapter.convert(inputFile, "mp3");
    }
}
