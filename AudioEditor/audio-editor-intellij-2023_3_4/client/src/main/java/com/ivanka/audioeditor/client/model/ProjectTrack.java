package com.ivanka.audioeditor.client.model;

public class ProjectTrack {
    public long id;
    public String trackName;
    public int trackOrder;

    public ProjectTrack() {}
    public ProjectTrack(long id, String trackName, int trackOrder) {
        this.id = id;
        this.trackName = trackName;
        this.trackOrder = trackOrder;
    }
}
