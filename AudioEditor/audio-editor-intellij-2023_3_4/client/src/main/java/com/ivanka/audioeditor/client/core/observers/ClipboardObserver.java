package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.ui.EditorContext;

import java.util.Arrays;

import static com.ivanka.audioeditor.client.ui.EditorContext.Selection;
import static com.ivanka.audioeditor.client.ui.EditorContext.TrackData;

public class ClipboardObserver implements Observer {
    private final EditorContext ctx;

    public ClipboardObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case CLIPBOARD_COPY -> onCopy(e);
            case CLIPBOARD_CUT -> onCut(e);
            case CLIPBOARD_PASTE -> onPaste(e);
            default -> {}
        }
    }

    private void onCopy(EditorEvent e) {
        String trackName = e.get("trackName");
        var td = ctx.getTrackDataMap().get(trackName);
        var sel = ctx.getSelections().get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        int[] range = selectionToByteRange(sel, td, 900);
        ctx.getClipboard().put(trackName, Arrays.copyOfRange(td.pcm, range[0], range[1]));
        ctx.toast("Copied " + (range[1] - range[0]) + " bytes");
    }

    private void onCut(EditorEvent e) {
        String trackName = e.get("trackName");
        var td = ctx.getTrackDataMap().get(trackName);
        var sel = ctx.getSelections().get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        int[] range = selectionToByteRange(sel, td, 900);
        ctx.getClipboard().put(trackName, Arrays.copyOfRange(td.pcm, range[0], range[1]));

        byte[] left = Arrays.copyOfRange(td.pcm, 0, range[0]);
        byte[] right = Arrays.copyOfRange(td.pcm, range[1], td.pcm.length);
        td.pcm = concat(left, right);
        td.framesCount = td.pcm.length / td.frameSize;
        td.durationSec = td.framesCount / td.frameRate;

        ctx.redrawTrack(trackName);
        sel.clear();
        ctx.toast("Cut " + (range[1] - range[0]) + " bytes");
    }

    private void onPaste(EditorEvent e) {
        String trackName = e.get("trackName");
        var td = ctx.getTrackDataMap().get(trackName);
        var sel = ctx.getSelections().get(trackName);
        var clip = ctx.getClipboard().get(trackName);
        if (td == null || clip == null) { ctx.toast("Clipboard empty"); return; }

        int insertAt;
        if (sel != null && (sel.xStart >= 0)) {
            int[] range = selectionToByteRange(new Selection(){{
                xStart = sel.xStart; xEnd = sel.xStart + 1;
            }}, td, 900);
            insertAt = range[0];
        } else {
            insertAt = td.pcm.length;
        }

        byte[] left = Arrays.copyOfRange(td.pcm, 0, insertAt);
        byte[] right = Arrays.copyOfRange(td.pcm, insertAt, td.pcm.length);
        td.pcm = concat(left, clip, right);
        td.framesCount = td.pcm.length / td.frameSize;
        td.durationSec = td.framesCount / td.frameRate;

        ctx.redrawTrack(trackName);
        ctx.toast("Pasted " + clip.length + " bytes");
    }

    // helpers
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
}
