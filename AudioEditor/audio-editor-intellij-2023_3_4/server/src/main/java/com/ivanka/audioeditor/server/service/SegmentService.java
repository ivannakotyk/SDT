package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.server.model.*;
import com.ivanka.audioeditor.server.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SegmentService {
    private final TrackRepository tracks;
    private final SegmentRepository segments;
    private final AudioFileRepository files;

    public SegmentService(TrackRepository tracks, SegmentRepository segments, AudioFileRepository files) {
        this.tracks = tracks;
        this.segments = segments;
        this.files = files;
    }
    public TrackEntity getTrack(Long id) {
        return tracks.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Track not found: " + id));
    }

    @Transactional
    public SegmentEntity addSegment(Long trackId, AudioFileEntity file, double start, double end) {
        TrackEntity track = getTrack(trackId);

        AudioFileEntity savedFile = file;
        if (file.getId() == null) {
            savedFile = files.save(file);
        }

        SegmentEntity segment = new SegmentEntity(track, savedFile, start, end);
        return segments.save(segment);
    }

    public List<SegmentEntity> listByTrack(Long trackId) {
        TrackEntity t = getTrack(trackId);
        return segments.findByTrackOrderByStartTimeSecAsc(t);
    }

    @Transactional
    public void deleteSegment(Long segmentId) {
        if (!segments.existsById(segmentId)) {
            throw new IllegalArgumentException("Segment not found: " + segmentId);
        }
        segments.deleteById(segmentId);
    }
}
