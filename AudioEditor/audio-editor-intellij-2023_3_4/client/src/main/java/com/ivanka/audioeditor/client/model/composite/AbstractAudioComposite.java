package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractAudioComposite implements AudioComponent {

    protected String name;
    protected final List<AudioComponent> children = new ArrayList<>();

    protected AudioFormat format = new AudioFormat(
            44100,
            16,
            1,
            true,
            false
    );

    protected volatile SourceDataLine line;

    protected AbstractAudioComposite(String name) {
        this.name = name;
    }

    @Override public String getName() { return name; }
    @Override public void rename(String name) { this.name = name; }

    @Override public void add(AudioComponent c) {
        children.add(c);
        if (c.getFormat() != null) {
            this.format = c.getFormat();
        }
    }

    @Override public void remove(AudioComponent c) { children.remove(c); }

    @Override public List<AudioComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public double getDurationSec() {
        return children.stream()
                .mapToDouble(AudioComponent::getDurationSec)
                .max().orElse(0.0);
    }

    @Override
    public void play() {
        for (AudioComponent c : children)
            c.play();
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
    public String infoTree(String indent) {
        var sb = new StringBuilder(indent)
                .append("• ")
                .append(getName())
                .append("\n");

        for (AudioComponent c : children)
            sb.append(c.infoTree(indent + "  "));

        return sb.toString();
    }

    @Override
    public void exportTo(File out, String format) throws Exception {
        throw new UnsupportedOperationException("Export not implemented in base class");
    }

    @Override
    public AudioFormat getFormat() {
        for (AudioComponent c : children) {
            AudioFormat f = c.getFormat();
            if (f != null) return f;
        }
        return PcmUtils.getStandardFormat();
    }
}