package com.ivanka.audioeditor.client.model.composite;

import com.ivanka.audioeditor.client.model.ProjectModel;
import java.io.File;

public class ModelFactory {

    public static ProjectModel toModel(AudioProject project, long userId) {

        ProjectModel pm = new ProjectModel();
        pm.userId = userId;
        pm.name = project.getName();

        for (AudioComponent c : project.getChildren()) {
            if (c instanceof AudioTrack t) {

                ProjectModel.Track pt = new ProjectModel.Track();
                pt.name = t.getName();

                for (AudioComponent leaf : t.getChildren()) {
                    if (leaf instanceof AudioSegment seg) {

                        ProjectModel.Segment ps = new ProjectModel.Segment();

                        ps.wavPath = saveSegmentToTemp(seg);
                        ps.start = 0;
                        ps.end = seg.getDurationSec();

                        pt.segments.add(ps);
                    }
                }

                pm.tracks.add(pt);
            }
        }

        return pm;
    }

    private static String saveSegmentToTemp(AudioSegment seg) {
        try {
            File out = File.createTempFile("seg-", ".wav");
            seg.exportTo(out, "wav");
            return out.getAbsolutePath();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
