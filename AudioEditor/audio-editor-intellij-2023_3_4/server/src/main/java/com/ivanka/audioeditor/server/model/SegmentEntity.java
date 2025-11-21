package com.ivanka.audioeditor.server.model;

import jakarta.persistence.*;

@Entity
public class SegmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private TrackEntity track;

    @ManyToOne(optional = false)
    private AudioFileEntity audioFile;

    private double startTimeSec;
    private double endTimeSec;

    public SegmentEntity() {}

    public SegmentEntity(TrackEntity track, AudioFileEntity file, double start, double end) {
        this.track = track;
        this.audioFile = file;
        this.startTimeSec = start;
        this.endTimeSec = end;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TrackEntity getTrack() { return track; }
    public void setTrack(TrackEntity track) { this.track = track; }

    public AudioFileEntity getAudioFile() { return audioFile; }
    public void setAudioFile(AudioFileEntity audioFile) { this.audioFile = audioFile; }

    public double getStartTimeSec() { return startTimeSec; }
    public void setStartTimeSec(double startTimeSec) { this.startTimeSec = startTimeSec; }

    public double getEndTimeSec() { return endTimeSec; }
    public void setEndTimeSec(double endTimeSec) { this.endTimeSec = endTimeSec; }
}