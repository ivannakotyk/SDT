package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.List;

public interface AudioComponent {

    void play();
    void stop();

    void exportTo(File out, String format) throws Exception;

    String getName();
    void rename(String name);

    double getDurationSec();
    AudioFormat getFormat();

    default void add(AudioComponent c) { throw new UnsupportedOperationException(); }
    default void remove(AudioComponent c) { throw new UnsupportedOperationException(); }
    default List<AudioComponent> getChildren() { return List.of(); }

    default AudioComponent getChild(int index) {
        return getChildren().get(index);
    }

    default String infoTree(String indent) {
        return indent + "• " + getName() + "\n";
    }
}
