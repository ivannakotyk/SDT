package com.ivanka.audioeditor.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectEntity {
    public long id;
    public String projectName;
    public String createdAt;
    public String updatedAt;
    public String filePath;
    public UserDTO user;
    public List<ProjectTrack> tracks;

    @Override
    public String toString() {
        return "ProjectEntity{" +
                "id=" + id +
                ", projectName='" + projectName + '\'' +
                ", user=" + (user != null ? user.userName : "null") +
                '}';
    }
}
