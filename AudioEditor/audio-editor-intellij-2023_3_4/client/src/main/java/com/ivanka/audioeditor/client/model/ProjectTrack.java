package com.ivanka.audioeditor.client.model;

public class ProjectTrack {
    private long id;
    private String trackName;
    private int trackOrder;

    public ProjectTrack() {
    }

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
    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public int getTrackOrder() {
        return trackOrder;
    }
    public void setTrackOrder(int trackOrder) {
        this.trackOrder = trackOrder;
    }
}