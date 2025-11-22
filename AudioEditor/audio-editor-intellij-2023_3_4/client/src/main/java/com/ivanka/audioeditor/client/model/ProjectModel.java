package com.ivanka.audioeditor.client.model;

public class ProjectModel {
    public long id;
    public String name;
    public long userId;

    public ProjectModel() {
    }

    public ProjectModel(long id, String name, long userId) {
        this.id = id;
        this.name = name;
        this.userId = userId;
    }
}