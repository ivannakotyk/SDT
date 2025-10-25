package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.ui.EditorContext;

import javax.sound.sampled.*;

import java.io.File;

public class PlaybackObserver implements Observer {
    private final EditorContext ctx;
    private Clip currentClip;

    public PlaybackObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case PLAYBACK_START -> onStart(e);
            case PLAYBACK_STOP -> onStop();
            default -> {}
        }
    }

    private void onStart(EditorEvent e) {
        try {
            onStop();
            String trackName = e.get("trackName");
            var td = ctx.getTrackDataMap().get(trackName);
            if (td != null) {
                File tmp = ctx.writeTrackTempWav(trackName, td);
                ctx.getTrackTempFiles().put(trackName, tmp);
            }
            File wav = ctx.getTrackTempFiles().get(trackName);
            if (wav == null || !wav.exists()) {
                ctx.alertWarn("No audio file imported for this track!");
                return;
            }
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(wav);
            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            currentClip.start();
            currentClip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    try { currentClip.close(); } catch (Exception ignored) {}
                }
            });
        } catch (UnsupportedAudioFileException ex) {
            ctx.alertError("Unsupported audio format (use WAV PCM)");
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Cannot play audio: " + ex.getMessage());
        }
    }

    private void onStop() {
        if (currentClip != null && currentClip.isOpen()) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
    }
}
