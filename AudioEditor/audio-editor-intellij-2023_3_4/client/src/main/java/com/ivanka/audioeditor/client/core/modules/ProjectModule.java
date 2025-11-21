package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.client.ui.EditorView;
import com.ivanka.audioeditor.client.ui.tree.ProjectTreeItem;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;

import java.util.List;
import java.util.Map;

public class ProjectModule extends AbstractColleague {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    public ProjectModule(EditorContext ctx) {
        this.ctx = ctx;
        setupContextMenu();
        loadUserProjects();
    }


    private void deleteProject(ProjectTreeItem item) {
        try {
            api.delete("/projects/" + item.getProjectId());
            item.getParent().getChildren().remove(item);

            if (ctx.getProject().id == item.getProjectId()) {
                ctx.getTracksPane().getChildren().clear();
                ctx.setProject(new ProjectModel());
                ctx.setCurrentProjectNode(null);
            }
            ctx.toast("Project deleted: " + item.getValue());

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Failed to delete project: " + ex.getMessage());
        }
    }

    private void loadUserProjects() {
        new Thread(() -> {
            try {
                long userId = ctx.getUserId();
                if (userId == 0) return;

                String json = api.get("/projects/by-user/" + userId);
                List<Map<String, Object>> list = mapper.readValue(json, new TypeReference<>() {});

                Platform.runLater(() -> {
                    ctx.getRootItem().getChildren().clear();
                    for (Map<String, Object> p : list) {
                        long id = ((Number) p.get("id")).longValue();
                        String name = (String) p.get("projectName");
                        ctx.getRootItem().getChildren().add(new ProjectTreeItem(id, name));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override public String key() { return "Project"; }

    @Override
    public void receive(EditorEvent e) {
        try {
            switch (e.type) {
                case PROJECT_CREATE_REQUEST -> onCreateProject(e);
                case PROJECT_SELECTED -> onProjectSelected(e);
                default -> {}
            }
        } catch (Exception ex) {
            ctx.alertError("Project error: " + ex.getMessage());
        }
    }

    private void onProjectSelected(EditorEvent e) {
        long pid = e.get("projectId");
        String pname = e.get("projectName");
        TreeItem<String> node = e.get("projectNode");

        var pm = new ProjectModel();
        pm.id = pid; pm.name = pname; pm.userId = ctx.getUserId();
        ctx.setProject(pm);
        ctx.setCurrentProjectNode(node);

        if (ctx instanceof EditorView ev) {
            ev.hydrateProjectFromServer(pid);
        }
    }

    private void onCreateProject(EditorEvent e) throws Exception {
        long userId = ctx.getUserId();
        String name = e.get("name");
        var req = Map.of("userId", userId, "projectName", name);
        String resp = api.postJson("/projects", req);
        JsonNode node = mapper.readTree(resp);
        long id = node.get("id").asLong();
        String pname = node.has("projectName") ? node.get("projectName").asText() : name;
        ProjectTreeItem item = new ProjectTreeItem(id, pname);
        ctx.getRootItem().getChildren().add(item);

        var pm = new ProjectModel();
        pm.id = id; pm.name = pname; pm.userId = userId;
        ctx.setProject(pm);
        ctx.setCurrentProjectNode(item);

        send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", item));
    }
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem renameItem = new MenuItem("✎ Rename");
        renameItem.setOnAction(e -> {
            TreeItem<String> selected = ctx.getTree().getSelectionModel().getSelectedItem();
            if (selected instanceof ProjectTreeItem pItem) {
                askAndRename(pItem);
            }
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> {
            TreeItem<String> selected = ctx.getTree().getSelectionModel().getSelectedItem();
            if (selected instanceof ProjectTreeItem pItem) {
                deleteProject(pItem);
            }
        });

        contextMenu.getItems().addAll(renameItem, new javafx.scene.control.SeparatorMenuItem(), deleteItem);
        ctx.getTree().setContextMenu(contextMenu);
    }

    private void askAndRename(ProjectTreeItem item) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(item.getValue());
        dialog.setTitle("Rename Project");
        dialog.setHeaderText("Enter new name for: " + item.getValue());
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.isBlank() || newName.equals(item.getValue())) return;

            try {
                String encodedName = java.net.URLEncoder.encode(newName.trim(), java.nio.charset.StandardCharsets.UTF_8);
                api.put("/projects/" + item.getProjectId() + "?name=" + encodedName);

                item.setValue(newName.trim());
                if (ctx.getProject().id == item.getProjectId()) {
                    ctx.getProject().name = newName.trim();
                    if (ctx.getAudioProject() != null) {
                        ctx.getAudioProject().rename(newName.trim());
                    }
                }
                ctx.toast("Renamed to: " + newName);

            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.alertError("Rename failed: " + ex.getMessage());
            }
        });
    }

}