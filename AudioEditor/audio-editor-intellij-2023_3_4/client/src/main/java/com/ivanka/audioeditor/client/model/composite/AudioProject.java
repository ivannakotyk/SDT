package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.*;
import java.io.File;

public class AudioProject extends AbstractAudioComposite {

    private final AudioFormat projectFormat;

    public AudioProject(String name) {
        super(name);
        this.projectFormat = PcmUtils.getStandardFormat();
    }

    @Override
    public AudioFormat getFormat() {
        return this.projectFormat;
    }

    @Override
    public double getDurationSec() {
        return children.stream()
                .mapToDouble(AudioComponent::getDurationSec)
                .max().orElse(0.0);
    }

    @Override
    public void play() {
        stop();
        try {
            float[][] mix = PcmUtils.mixProject(this);
            byte[] pcm = PcmUtils.toPCM16(mix);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, projectFormat);

            SourceDataLine localLine = (SourceDataLine) AudioSystem.getLine(info);
            this.line = localLine;

            localLine.open(projectFormat);
            localLine.start();
            localLine.write(pcm, 0, pcm.length);
            localLine.drain();

        } catch (Exception ex) {
            System.out.println("Playback interrupted (or error): " + ex.getMessage());

        } finally {
            if (this.line != null) {
                try {
                    this.line.close();
                } catch (Exception ignore) {}
                this.line = null;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();

        for (AudioComponent c : children)
            c.stop();
    }

    @Override
    public void exportTo(File out, String ext) throws Exception {
        float[][] mix = PcmUtils.mixProject(this);
        PcmUtils.writeWav(mix, this.projectFormat, out);
    }
}