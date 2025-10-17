package com.ivanka.audioeditor.server.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String projectName;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"projects"})
    private AppUser user;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("trackOrder ASC")
    @JsonIgnore
    private List<TrackEntity> tracks = new ArrayList<>();

    public ProjectEntity() {}

    public ProjectEntity(String projectName, AppUser user) {
        this.projectName = projectName;
        this.user = user;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }


    public Long getId() { return id; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public List<TrackEntity> getTracks() { return tracks; }
    public void setTracks(List<TrackEntity> tracks) { this.tracks = tracks; }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
