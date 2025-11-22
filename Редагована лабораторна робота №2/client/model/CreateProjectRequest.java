package com.ivanka.audioeditor.client.model;

public class CreateProjectRequest {
    private Long userId;
    private String projectName;

    public CreateProjectRequest(Long userId, String projectName) {
        this.userId = userId;
        this.projectName = projectName;
    }

    public Long getUserId() { return userId; }
    public String getProjectName() { return projectName; }
}