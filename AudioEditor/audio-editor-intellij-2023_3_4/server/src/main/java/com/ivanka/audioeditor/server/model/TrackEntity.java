package com.ivanka.audioeditor.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
public class TrackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trackName;
    private int trackOrder;
    private double volume = 1.0;
    private boolean muted = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    @JsonIgnoreProperties({"tracks", "user"})
    private ProjectEntity project;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTimeSec ASC")
    @JsonIgnoreProperties("track")
    private List<SegmentEntity> segments = new ArrayList<>();

    public TrackEntity() {}

    public TrackEntity(ProjectEntity project, String name, int order) {
        this.project = project;
        this.trackName = name;
        this.trackOrder = order;
    }

    public Long getId() { return id; }
    public String getTrackName() { return trackName; }
    public void setTrackName(String trackName) { this.trackName = trackName; }
    public int getTrackOrder() { return trackOrder; }
    public void setTrackOrder(int trackOrder) { this.trackOrder = trackOrder; }
    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) { this.project = project; }
    public List<SegmentEntity> getSegments() { return segments; }
}