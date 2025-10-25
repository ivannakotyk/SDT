package com.ivanka.audioeditor.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AudioEditor implements Subject {
    private static final AudioEditor INSTANCE = new AudioEditor();

    private final List<Observer> observers = new ArrayList<>();
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private AudioEditor() { }

    public static AudioEditor getInstance() { return INSTANCE; }

    @Override
    public synchronized void attach(Observer o) {
        if (!observers.contains(o)) observers.add(o);
    }

    @Override
    public synchronized void detach(Observer o) {
        observers.remove(o);
    }

    @Override
    public synchronized void notifyObservers(EditorEvent event) {
        List<Observer> snapshot = new ArrayList<>(observers);
        for (Observer o : snapshot) {
            try { o.update(event); } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    public ProjectModel createNewProject(long userId, String name) throws Exception {
        var requestBody = Map.of(
                "userId", userId,
                "projectName", name
        );

        String response = api.postJson("/projects", requestBody);

        var node = mapper.readTree(response);
        ProjectModel pm = new ProjectModel();
        pm.id = node.get("id").asLong();
        pm.name = node.has("projectName") ? node.get("projectName").asText() : name;
        return pm;

    }

}
