package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.ui.EditorContext;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Arrays;

import static com.ivanka.audioeditor.client.ui.EditorContext.TrackData;

public class ClipboardModule extends AbstractColleague {
    private final EditorContext ctx;

    private static class ClipboardBuf {
        final AudioFormat format;
        final byte[] pcm;
        ClipboardBuf(AudioFormat f, byte[] p) { this.format = f; this.pcm = p; }
        boolean isEmpty() { return pcm == null || pcm.length == 0 || format == null; }
    }
    private static ClipboardBuf CLIPBOARD = null;

    public ClipboardModule(EditorContext ctx) { this.ctx = ctx; }
    @Override public String key() { return "Clipboard"; }

    @Override
    public void receive(EditorEvent e) {
        try {
            switch (e.type) {
                case CLIPBOARD_COPY -> onCopy(e);
                case CLIPBOARD_CUT  -> onCut(e);
                case CLIPBOARD_PASTE-> onPaste(e);
                default -> {}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Clipboard error: " + ex.getMessage());
        }
    }

    private void onCopy(EditorEvent e) throws Exception {
        String track = e.get("trackName");
        var td = ctx.getTrackDataMap().get(track);
        var sel = ctx.getSelections().get(track);
        if (td == null || sel == null || !sel.isActive()) {
            ctx.alertWarn("Select a segment to copy.");
            return;
        }
        int[] br = selectionToByteRange(sel, td, 900);
        byte[] seg = Arrays.copyOfRange(td.pcm, br[0], br[1]);
        CLIPBOARD = new ClipboardBuf(td.format, seg);
        ctx.toast("Copied " + (br[1]-br[0]) + " bytes.");
    }

    private void onCut(EditorEvent e) throws Exception {
        String track = e.get("trackName");
        var td = ctx.getTrackDataMap().get(track);
        var sel = ctx.getSelections().get(track);
        if (td == null || sel == null || !sel.isActive()) {
            ctx.alertWarn("Select a segment to cut.");
            return;
        }
        int[] br = selectionToByteRange(sel, td, 900);
        byte[] seg = Arrays.copyOfRange(td.pcm, br[0], br[1]);
        CLIPBOARD = new ClipboardBuf(td.format, seg);

        byte[] left = Arrays.copyOfRange(td.pcm, 0, br[0]);
        byte[] right = Arrays.copyOfRange(td.pcm, br[1], td.pcm.length);
        td.pcm = concat(left, right);

        refreshTrackAfterPcmChange(track, td);
        sel.clear();
        ctx.redrawTrack(track);
        ctx.toast("Cut " + seg.length + " bytes.");
    }
    private void onPaste(EditorEvent e) throws Exception {
        String track = e.get("trackName");
        var td = ctx.getTrackDataMap().get(track);
        if (td == null) { ctx.alertWarn("No track audio to paste into."); return; }
        if (CLIPBOARD == null || CLIPBOARD.isEmpty()) { ctx.alertWarn("Clipboard is empty."); return; }

        byte[] pastePcm = ensureClipboardToTrackFormat(CLIPBOARD, td);

        var sel = ctx.getSelections().get(track);
        if (sel != null && sel.isActive()) {
            // REPLACE selection
            int[] br = selectionToByteRange(sel, td, 900);
            byte[] left = Arrays.copyOfRange(td.pcm, 0, br[0]);
            byte[] right = Arrays.copyOfRange(td.pcm, br[1], td.pcm.length);
            td.pcm = concat(left, pastePcm, right);
            sel.clear();
        } else {
            Double cursorFrac = e.get("cursorFrac");
            double frac = (cursorFrac == null) ? 0.0 : Math.max(0.0, Math.min(1.0, cursorFrac));
            int ins = (int)Math.round(td.pcm.length * frac);
            ins = (ins / td.frameSize) * td.frameSize;
            byte[] left = Arrays.copyOfRange(td.pcm, 0, ins);
            byte[] right = Arrays.copyOfRange(td.pcm, ins, td.pcm.length);
            td.pcm = concat(left, pastePcm, right);
        }

        refreshTrackAfterPcmChange(track, td);
        ctx.redrawTrack(track);
        ctx.toast("Pasted " + pastePcm.length + " bytes.");
    }

    private void refreshTrackAfterPcmChange(String trackName, TrackData td) {
        td.framesCount = td.pcm.length / td.frameSize;
        td.durationSec = td.frameRate > 0 ? td.framesCount / td.frameRate : 0.0;
        try {
            File newTmp = ctx.writeTrackTempWav(trackName, td);
            ctx.getTrackTempFiles().put(trackName, newTmp);
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Failed to update temp WAV after edit: " + ex.getMessage());
        }
    }

    private int[] selectionToByteRange(EditorContext.Selection sel, TrackData td, double canvasWidth) {
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

    private byte[] ensureClipboardToTrackFormat(ClipboardBuf clip, TrackData td) throws Exception {
        if (clip == null || clip.isEmpty()) return new byte[0];

        AudioFormat target = td.format;
        if (formatsEqualLoose(clip.format, target)) {
            return clip.pcm;
        }

        try {
            File src = ctx.writePcmToTempWav(clip.format, clip.pcm);
            File out = File.createTempFile("cb2trk-", ".wav");
            String sr = Integer.toString((int) target.getSampleRate());
            String ch = Integer.toString(target.getChannels());

            var cmd = java.util.List.of(
                    "ffmpeg", "-y",
                    "-i", src.getAbsolutePath(),
                    "-ar", sr, "-ac", ch, "-sample_fmt", "s16",
                    out.getAbsolutePath()
            );
            int code = runProcess(cmd);
            if (code != 0) throw new RuntimeException("ffmpeg convert clipboard->track failed: exit " + code);
            TrackData segNew = ctx.readWavToMemory(out);
            if (segNew.frameSize != td.frameSize && segNew.format != null) {}
            return segNew.pcm;
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Clipboard convert failed: " + ex.getMessage());
            return clip.pcm;
        }
    }

    private boolean formatsEqualLoose(AudioFormat a, AudioFormat b) {
        if (a == null || b == null) return false;
        return approx(a.getSampleRate(), b.getSampleRate())
                && a.getChannels() == b.getChannels()
                && a.getSampleSizeInBits() == b.getSampleSizeInBits();
    }

    private boolean approx(float x, float y) { return Math.abs(x - y) < 0.5f; }

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
