package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.*;
import java.io.File;

public class AudioSegment implements AudioComponent {

    private long id;
    private String name;
    private float[][] samples;
    private final AudioFormat format;

    private volatile SourceDataLine line;

    public AudioSegment(String name, float[][] samples, AudioFormat fmt) {
        this.name = name;
        this.samples = samples;
        this.format = fmt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public void setSamples(float[][] newSamples) {
        stop();
        this.samples = newSamples;
    }

    @Override
    public void play() {
        stop();
        try {
            byte[] buf = PcmUtils.toPCM16(samples);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine localLine = (SourceDataLine) AudioSystem.getLine(info);
            this.line = localLine;
            localLine.open(format);
            localLine.start();
            localLine.write(buf, 0, buf.length);
            localLine.drain();
        } catch (Exception ex) {
            System.out.println("Segment playback interrupted: " + ex.getMessage());
        } finally {
            if (this.line != null) {
                try { this.line.close(); } catch (Exception ignore) {}
                this.line = null;
            }
        }
    }

    @Override
    public void stop() {
        SourceDataLine lineToStop = this.line;
        if (lineToStop != null) {
            try {
                lineToStop.stop();
                lineToStop.close();
            } catch (Exception ignore) {
            } finally {
                if (this.line == lineToStop) {
                    this.line = null;
                }
            }
        }
    }

    @Override
    public double getDurationSec() {
        if (samples == null || samples.length == 0) return 0.0;
        return samples[0].length / format.getSampleRate();
    }

    @Override
    public void exportTo(File out, String formatExt) throws Exception {
        PcmUtils.writeWav(this.samples, this.format, out);
    }

    @Override public String getName() { return name; }
    @Override public void rename(String newName) { this.name = newName; }

    public float[][] getSamples() { return samples; }

    @Override
    public AudioFormat getFormat() { return format; }
}