package com.ivanka.audioeditor.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.model.ProjectEntity;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.net.ApiClient;

public class AudioEditor {
    private static AudioEditor INSTANCE;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private ProjectEntity currentProject;
    private ProjectModel activeProject;
    private final ObservableBus bus = new ObservableBus();

    private AudioEditor() {}

    public static synchronized AudioEditor getInstance() {
        if (INSTANCE == null) INSTANCE = new AudioEditor();
        return INSTANCE;
    }

    public ObservableBus events() { return bus; }

    public ProjectEntity getCurrentProject() { return currentProject; }

    public ProjectEntity createNewProject(long userId, String name) throws Exception {
        var payload = new CreateProject(userId, name);
        String json = api.postJson("/projects", payload);
        currentProject = mapper.readValue(json, ProjectEntity.class);
        bus.emit("project:created", currentProject);
        return currentProject;
    }

    public record CreateProject(long userId, String projectName) {}
}
