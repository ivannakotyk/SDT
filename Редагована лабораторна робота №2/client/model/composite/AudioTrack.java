package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.AudioFormat;
import java.io.File;

public class AudioTrack extends AbstractAudioComposite {

    public AudioTrack(String name) {
        super(name);
    }

    @Override
    public void play() {
        for (AudioComponent c : children)
            c.play();
    }

    @Override
    public void stop() {
        for (AudioComponent c : children)
            c.stop();
    }

    @Override
    public void exportTo(File out, String formatExt) throws Exception {
        PcmUtils.concatTrackToFile(this, out, formatExt);
    }

    @Override
    public AudioFormat getFormat() {
        for (AudioComponent c : children) {
            if (c instanceof AudioSegment s) {
                return s.getFormat();
            }
        }
        return PcmUtils.getStandardFormat();
    }
}