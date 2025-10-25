package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.scene.control.TreeItem;

import java.util.List;

public class ProjectObserver implements Observer {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();

    public ProjectObserver(EditorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case PROJECT_CREATE_REQUEST -> onCreateRequest(e);
            case PROJECT_CREATED -> onCreated(e);
            case PROJECT_SELECTED -> onSelected(e);
            case TRACKS_REFRESH_REQUEST -> onRefresh(e);
            default -> {}
        }
    }

    private void onCreateRequest(EditorEvent e) {
        try {
            long userId = ctx.getUserId();
            String name = e.get("name");
            ProjectModel newProj = AudioEditor.getInstance().createNewProject(userId, name);
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.PROJECT_CREATED).with("project", newProj)
            );
        } catch (Exception ex) {
            ctx.alertError("Failed to create project: " + ex.getMessage());
        }
    }

    private void onCreated(EditorEvent e) {
        ProjectModel pm = e.get("project");
        ctx.setProject(pm);
        TreeItem<String> projNode = new TreeItem<>(" " + pm.name);
        projNode.setExpanded(true);
        ctx.getRootItem().getChildren().add(projNode);
        ctx.setCurrentProjectNode(projNode);
        AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", projNode)
        );
    }

    private void onSelected(EditorEvent e) {
        long projectId = e.get("projectId");
        String projectName = e.get("projectName");
        TreeItem<String> projectNode = e.get("projectNode");

        ProjectModel pm = new ProjectModel();
        pm.id = projectId;
        pm.name = projectName;
        ctx.setProject(pm);
        ctx.setCurrentProjectNode(projectNode);
        ctx.getTracksPane().getChildren().clear();
        AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", projectNode)
        );
    }


    @SuppressWarnings("unchecked")
    private void onRefresh(EditorEvent e) {
        try {
            TreeItem<String> projectNode = e.get("projectNode");
            if (ctx.getCurrentProjectNode() != projectNode) {
                ctx.getTracksPane().getChildren().clear();
                ctx.setCurrentProjectNode(projectNode);
            }

            List<ProjectTrack> tracks;
            var cache = ctx.getTrackCache();
            var project = ctx.getProject();

            if (cache.containsKey(project.id)) {
                tracks = cache.get(project.id);
            } else {
                String res = api.get("/projects/" + project.id + "/tracks?nocache=" + System.nanoTime());
                tracks = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        res,
                        new com.fasterxml.jackson.core.type.TypeReference<List<ProjectTrack>>() {}
                );
                cache.put(project.id, tracks);
            }

            projectNode.getChildren().clear();
            if (tracks.isEmpty()) {
                projectNode.getChildren().add(new TreeItem<>("— No tracks yet —"));
                ctx.getTracksPane().getChildren().clear();
                var placeholder = new javafx.scene.control.Label("This project is empty. Add a track to get started!");
                placeholder.setTextFill(javafx.scene.paint.Color.web("#aaa"));
                ctx.getTracksPane().getChildren().add(placeholder);
                return;
            }

            for (ProjectTrack t : tracks) {
                TreeItem<String> trackNode = new TreeItem<>(t.getTrackName());
                projectNode.getChildren().add(trackNode);

                AudioEditor.getInstance().notifyObservers(
                        new EditorEvent(EditorEventType.TRACK_ADDED)
                                .with("projectId", project.id)
                                .with("trackName", t.getTrackName())
                );
            }
            projectNode.setExpanded(true);
            ctx.getTracksPane().requestLayout();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
