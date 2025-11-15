package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioSegment;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.model.composite.PcmUtils;
import com.ivanka.audioeditor.client.ui.EditorContext;

import javax.sound.sampled.AudioFormat;
import java.io.File;

import static com.ivanka.audioeditor.client.ui.EditorContext.Selection;

public class WaveformModule extends AbstractColleague {
    private final EditorContext ctx;

    public WaveformModule(EditorContext ctx) { this.ctx = ctx; }
    @Override public String key() { return "Waveform"; }

    @Override
    public void receive(EditorEvent e) {
        switch (e.type) {
            case AUDIO_IMPORTED -> ctx.redrawTrack(e.get("trackName"));
            case WAVEFORM_REDRAW -> onWaveformFx(e);
            default -> {}
        }
    }

    private void onWaveformFx(EditorEvent e) {
        String trackName = e.get("trackName");
        String fx = e.get("fx");

        AudioSegment mainSegment = getMainSegment(trackName);
        if (mainSegment == null) {
            ctx.alertWarn("No audio segment found for this track.");
            return;
        }

        var sel = ctx.getSelections().get(trackName);
        if (sel == null || !sel.isActive()) return;

        try {
            int[] range = selectionToSampleRange(sel, mainSegment, 900);
            float[][] samples = mainSegment.getSamples();

            float[][] newSamples = null;

            if ("reverse".equals(fx)) {
                newSamples = PcmUtils.reverse(samples, range[0], range[1]);

            } else if (fx != null && fx.startsWith("atempo:")) {
                double k = Double.parseDouble(fx.substring("atempo:".length()));
                float[][] slice = PcmUtils.slice(samples, range[0], range[1]);
                File inWav = File.createTempFile("seg-tempo-in-", ".wav");
                PcmUtils.writeWav(slice, mainSegment.getFormat(), inWav);
                File outWav = java.io.File.createTempFile("seg-tempo-out-", ".wav");
                var cmd = java.util.List.of("ffmpeg", "-y", "-i", inWav.getAbsolutePath(),
                        "-filter:a", "atempo=" + k, outWav.getAbsolutePath());
                int code = runProcess(cmd);
                if (code != 0) throw new RuntimeException("ffmpeg atempo failed: exit " + code);

                AudioFormat[] fmt = new AudioFormat[1];
                float[][] newSlice = PcmUtils.readWavStereo(outWav, fmt);
                newSamples = PcmUtils.splice(samples, newSlice, range[0], range[1]);
                inWav.delete();
                outWav.delete();
            }

            if (newSamples != null) {
                mainSegment.setSamples(newSamples);
            }

            ctx.redrawTrack(trackName);

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Waveform effect failed: " + ex.getMessage());
        }
    }
    private AudioTrack getTrack(String name) {
        AudioProject project = ctx.getAudioProject();
        if (project == null) return null;
        return (AudioTrack) project.getChildren().stream()
                .filter(c -> c instanceof AudioTrack && c.getName().equals(name))
                .findFirst().orElse(null);
    }

    private AudioSegment getMainSegment(String trackName) {
        AudioTrack track = getTrack(trackName);
        if (track == null || track.getChildren().isEmpty()) return null;
        return (AudioSegment) track.getChildren().get(0);
    }

    private int[] selectionToSampleRange(Selection sel, AudioSegment seg, double canvasWidth) {
        double L = Math.max(0, Math.min(sel.left(), canvasWidth));
        double R = Math.max(0, Math.min(sel.right(), canvasWidth));
        double fracL = L / canvasWidth;
        double fracR = R / canvasWidth;

        int totalSamples = seg.getSamples()[0].length;
        int start = (int) Math.floor(totalSamples * fracL);
        int end   = (int) Math.ceil (totalSamples * fracR);
        end = Math.min(end, totalSamples);
        if (end <= start) end = Math.min(start + 1, totalSamples);
        return new int[]{start, end};
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