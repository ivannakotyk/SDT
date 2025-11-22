package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.*;
import com.ivanka.audioeditor.client.ui.EditorContext;

public class ClipboardModule extends AbstractColleague {

    private final EditorContext ctx;
    private static AudioSegment clipboardSegment = null;

    public ClipboardModule(EditorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String key() {
        return "Clipboard";
    }

    @Override
    public void receive(EditorEvent e) {
        try {
            EditorEventType type = e.type;
            switch (type) {
                case CLIPBOARD_COPY -> onCopy(e);
                case CLIPBOARD_CUT  -> onCut(e);
                case CLIPBOARD_PASTE -> onPaste(e);
                default -> {}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Clipboard error: " + ex.getMessage());
        }
    }
    private void onCopy(EditorEvent e) {
        String trackName = e.get("trackName");
        AudioSegment mainSegment = getMainSegment(trackName);
        var sel = ctx.getSelections().get(trackName);

        if (mainSegment == null || sel == null || !sel.isActive()) {
            ctx.alertWarn("Select a segment to copy.");
            return;
        }

        int[] range = selectionToSampleRange(sel, mainSegment, 900);
        float[][] slice = PcmUtils.slice(mainSegment.getSamples(), range[0], range[1]);

        clipboardSegment = new AudioSegment("clip", slice, mainSegment.getFormat());
        ctx.toast("Copied " + slice[0].length + " samples.");
    }
    private void onCut(EditorEvent e) {
        String trackName = e.get("trackName");
        AudioSegment mainSegment = getMainSegment(trackName);
        var sel = ctx.getSelections().get(trackName);
        if (mainSegment == null || sel == null || !sel.isActive()) {
            ctx.alertWarn("Select a segment to cut.");
            return;
        }

        int[] range = selectionToSampleRange(sel, mainSegment, 900);
        float[][] slice = PcmUtils.slice(mainSegment.getSamples(), range[0], range[1]);
        clipboardSegment = new AudioSegment("clip", slice, mainSegment.getFormat());
        ctx.toast("Cut " + slice[0].length + " samples.");

        float[][] remaining = PcmUtils.cut(mainSegment.getSamples(), range[0], range[1]);
        mainSegment.setSamples(remaining);
        sel.clear();
        ctx.redrawTrack(trackName);
    }

    private void onPaste(EditorEvent e) throws Exception {
        String trackName = e.get("trackName");
        AudioSegment mainSegment = getMainSegment(trackName);

        if (mainSegment == null) {
            ctx.alertWarn("No track audio to paste into.");
            return;
        }
        if (clipboardSegment == null) {
            ctx.alertWarn("Clipboard is empty.");
            return;
        }

        float[][] clipSamples = clipboardSegment.getSamples();
        float[][] mainSamples = mainSegment.getSamples();

        var sel = ctx.getSelections().get(trackName);
        float[][] newSamples;

        if (sel != null && sel.isActive()) {
            int[] range = selectionToSampleRange(sel, mainSegment, 900);
            newSamples = PcmUtils.splice(mainSamples, clipSamples, range[0], range[1]);
            sel.clear();
        } else {
            Double cursorFrac = e.get("cursorFrac");
            double frac = (cursorFrac == null) ? 0.0 : Math.max(0.0, Math.min(1.0, cursorFrac));
            int totalSamples = mainSamples[0].length;
            int samplePos = (int) Math.round(totalSamples * frac);
            newSamples = PcmUtils.splice(mainSamples, clipSamples, samplePos, samplePos);
        }

        mainSegment.setSamples(newSamples);
        ctx.redrawTrack(trackName);
        ctx.toast("Pasted " + clipSamples[0].length + " samples.");
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

    private int[] selectionToSampleRange(EditorContext.Selection sel, AudioSegment seg, double canvasWidth) {
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
}