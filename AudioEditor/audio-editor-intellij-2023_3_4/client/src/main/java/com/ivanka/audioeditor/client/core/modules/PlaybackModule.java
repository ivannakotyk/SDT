package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.model.composite.PcmUtils;
import com.ivanka.audioeditor.client.ui.EditorContext;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlaybackModule extends AbstractColleague {
    private final EditorContext ctx;
    private Clip currentClip;
    private long pausedAtMicros = 0L;
    private String playingTrack = null;

    private final ScheduledExecutorService progressExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "playback-progress");
                t.setDaemon(true);
                return t;
            });

    public PlaybackModule(EditorContext ctx) { this.ctx = ctx; }
    @Override public String key() { return "Playback"; }

    @Override
    public void receive(EditorEvent e) {
        switch (e.type) {
            case PLAYBACK_START -> onStart(e);
            case PLAYBACK_STOP  -> onStop();
            default -> {}
        }
    }

    private void onStart(EditorEvent e) {
        try {
            String trackName = e.get("trackName");
            Double startAtSec = null;
            Object s = e.get("startAtSec");
            if (s instanceof Number n) startAtSec = n.doubleValue();

            closeClipQuietly();

            AudioProject project = ctx.getAudioProject();
            if (project == null) return;
            AudioTrack track = (AudioTrack) project.getChildren().stream()
                    .filter(c -> c instanceof AudioTrack && c.getName().equals(trackName))
                    .findFirst().orElse(null);

            if (track == null || track.getChildren().isEmpty()) {
                ctx.alertWarn("No audio data found for this track!");
                return;
            }

            AudioFormat format = track.getFormat();
            float[][] samples = PcmUtils.concatTrack(track);
            byte[] pcm = PcmUtils.toPCM16(samples);

            if (pcm.length == 0) {
                ctx.alertWarn("Track is empty.");
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
                 AudioInputStream audioStream = new AudioInputStream(bais, format, pcm.length / format.getFrameSize())) {

                currentClip = AudioSystem.getClip();
                currentClip.open(audioStream);
            }

            long usePos = (startAtSec != null) ? (long) (startAtSec * 1_000_000L) : pausedAtMicros;
            if (usePos > 0 && usePos < currentClip.getMicrosecondLength()) {
                currentClip.setMicrosecondPosition(usePos);
            } else {
                currentClip.setMicrosecondPosition(0);
            }

            playingTrack = trackName;
            currentClip.addLineListener(ev -> {
                if (currentClip == null || !playingTrack.equals(trackName)) return;

                if (ev.getType() == LineEvent.Type.STOP) {
                    long pos = currentClip.getMicrosecondPosition();
                    long len = currentClip.getMicrosecondLength();
                    if (len > 0 && pos >= len - 1000) {
                        send(new EditorEvent(EditorEventType.PLAYBACK_FINISHED)
                                .with("trackName", playingTrack));
                        onStop();
                    }
                }
            });

            currentClip.start();
            pausedAtMicros = 0L;
            progressExec.scheduleAtFixedRate(this::tickProgress, 0, 50, TimeUnit.MILLISECONDS);

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Cannot play audio: " + ex.getMessage());
        }
    }

    private void tickProgress() {
        try {
            Clip c = currentClip;
            String tr = playingTrack;
            if (c == null || tr == null || !c.isRunning()) return;

            long len = c.getMicrosecondLength();
            long pos = c.getMicrosecondPosition();
            if (len <= 0) return;

            double frac = Math.max(0, Math.min(1, (double) pos / (double) len));

            send(new EditorEvent(EditorEventType.PLAYBACK_PROGRESS)
                    .with("trackName", tr)
                    .with("fraction", frac));

        } catch (Exception ignore) {}
    }

    private void onStop() {
        if (currentClip != null) {
            try {
                if (currentClip.isRunning() ||
                        currentClip.getMicrosecondPosition() < currentClip.getMicrosecondLength() - 1000) {
                    pausedAtMicros = currentClip.getMicrosecondPosition();
                } else {
                    pausedAtMicros = 0L;
                }
            } catch (Exception ignored) {
                pausedAtMicros = 0L;
            }
        }
        closeClipQuietly();
        playingTrack = null;
    }

    private void closeClipQuietly() {
        if (currentClip != null) {
            try { currentClip.stop(); } catch (Exception ignore) {}
            try { currentClip.close(); } catch (Exception ignore) {}
            currentClip = null;
        }
    }
}