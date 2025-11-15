package com.ivanka.audioeditor.server.service.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class FFmpegAdapter {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public File convert(File inputFile, String targetFormat) throws Exception {
        String baseName = inputFile.getName().replaceAll("\\.[^.]+$", "");
        File out = File.createTempFile(baseName + "-", "." + targetFormat);

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputFile.getAbsolutePath());
        if ("mp3".equalsIgnoreCase(targetFormat)) {
            cmd.add("-codec:a"); cmd.add("libmp3lame");
            cmd.add("-qscale:a"); cmd.add("2");
        }
        if ("ogg".equalsIgnoreCase(targetFormat)) {
            cmd.add("-codec:a"); cmd.add("libvorbis");
            cmd.add("-qscale:a"); cmd.add("5");
        }

        cmd.add(out.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (br.readLine() != null) { /* log consume */ }
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("ffmpeg exited with " + code);
        }
        return out;
    }
}