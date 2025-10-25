package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.ui.EditorContext;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static com.ivanka.audioeditor.client.ui.EditorContext.Selection;
import static com.ivanka.audioeditor.client.ui.EditorContext.TrackData;

public class WaveformViewObserver implements Observer {
    private final EditorContext ctx;

    public WaveformViewObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case AUDIO_IMPORTED -> onAudioImported(e);
            case WAVEFORM_REDRAW -> onWaveformFx(e);
            default -> {}
        }
    }

    private void onAudioImported(EditorEvent e) {
        String trackName = e.get("trackName");
        TrackData td = e.get("trackData");
        ctx.getTrackDataMap().put(trackName, td);
        try {
            File tmp = ctx.writeTrackTempWav(trackName, td);
            ctx.getTrackTempFiles().put(trackName, tmp);
        } catch (Exception ex) { ex.printStackTrace(); }

        ctx.redrawTrack(trackName);
    }

    private void onWaveformFx(EditorEvent e) {
        String trackName = e.get("trackName");
        String fx = e.get("fx");

        var td = ctx.getTrackDataMap().get(trackName);
        if (td == null) return;

        var sel = ctx.getSelections().get(trackName);
        if (sel == null || !sel.isActive()) return;

        try {
            int[] range = selectionToByteRange(sel, td, 900);
            if ("reverse".equals(fx)) {
                reverseInPlaceByFrames(td.pcm, range[0], range[1], td.frameSize);
            } else if (fx != null && fx.startsWith("atempo:")) {
                double k = Double.parseDouble(fx.substring("atempo:".length()));
                File inWav = ctx.writePcmToTempWav(td.format, Arrays.copyOfRange(td.pcm, range[0], range[1]));
                File outWav = java.io.File.createTempFile("seg-tempo-", ".wav");
                var cmd = java.util.List.of("ffmpeg", "-y", "-i", inWav.getAbsolutePath(), "-filter:a", "atempo=" + k, outWav.getAbsolutePath());
                int code = runProcess(cmd);
                if (code != 0) throw new RuntimeException("ffmpeg atempo failed: exit " + code);
                TrackData segNew = ctx.readWavToMemory(outWav);
                byte[] left = java.util.Arrays.copyOfRange(td.pcm, 0, range[0]);
                byte[] right = java.util.Arrays.copyOfRange(td.pcm, range[1], td.pcm.length);
                td.pcm = concat(left, segNew.pcm, right);
                td.framesCount = td.pcm.length / td.frameSize;
                td.durationSec = td.framesCount / td.frameRate;
            }

            ctx.redrawTrack(trackName);
            ctx.toast(Objects.equals(fx, "reverse") ? "Reversed segment" : "Tempo applied: " + fx);

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Waveform effect failed: " + ex.getMessage());
        }
    }

    private int[] selectionToByteRange(Selection sel, TrackData td, double canvasWidth) {
        double L = Math.max(0, Math.min(sel.left(), canvasWidth));
        double R = Math.max(0, Math.min(sel.right(), canvasWidth));
        double fracL = L / canvasWidth;
        double fracR = R / canvasWidth;

        int start = (int) Math.floor(td.pcm.length * fracL);
        int end = (int) Math.ceil(td.pcm.length * fracR);

        start = (start / td.frameSize) * td.frameSize;
        end = (end / td.frameSize) * td.frameSize;
        end = Math.min(end, td.pcm.length);
        if (end <= start) end = Math.min(start + td.frameSize, td.pcm.length);
        return new int[]{start, end};
    }

    private void reverseInPlaceByFrames(byte[] arr, int from, int to, int frameSize) {
        int l = from;
        int r = to - frameSize;
        while (l < r) {
            for (int i = 0; i < frameSize; i++) {
                byte tmp = arr[l + i];
                arr[l + i] = arr[r + i];
                arr[r + i] = tmp;
            }
            l += frameSize;
            r -= frameSize;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private int runProcess(java.util.List<String> cmd) throws java.io.IOException, InterruptedException {
        var pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        var p = pb.start();
        try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) {}
        }
        return p.waitFor();
    }
}
