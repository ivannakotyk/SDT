package com.ivanka.audioeditor.client.model.composite;

import com.ivanka.audioeditor.client.model.ProjectModel;
import javax.sound.sampled.AudioFormat;
import java.io.File;

public class CompositeFactory {

    public static AudioProject fromModel(ProjectModel model) {
        AudioProject project = new AudioProject(model.name);

        for (ProjectModel.Track t : model.tracks) {
            AudioTrack track = new AudioTrack(t.name);

            for (ProjectModel.Segment seg : t.segments) {
                try {
                    File wav = new File(seg.wavPath);

                    AudioFormat[] fmt = new AudioFormat[1];
                    float[][] stereo = PcmUtils.readWavStereo(wav, fmt);

                    AudioSegment leaf =
                            new AudioSegment(seg.id + "_" + seg.wavPath, stereo, fmt[0]);

                    track.add(leaf);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            project.add(track);
        }
        return project;
    }
}