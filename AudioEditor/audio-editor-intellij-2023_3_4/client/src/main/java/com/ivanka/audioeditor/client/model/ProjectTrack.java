package com.ivanka.audioeditor.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectTrack {
    private long id;
    private String trackName;
    private int trackOrder;

    public ProjectTrack(long id, String trackName, int trackOrder) {
        this.id = id;
        this.trackName = trackName;
        this.trackOrder = trackOrder;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTrackName() {
        return trackName;
    }


}