package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.client.ui.tree.ProjectTreeItem;
import javafx.scene.control.TreeItem;
import com.ivanka.audioeditor.common.dto.CreateProjectRequest;
import com.ivanka.audioeditor.common.dto.ProjectResponse;

import java.util.Map;

public class ProjectModule extends AbstractColleague {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    public ProjectModule(EditorContext ctx) { this.ctx = ctx; }

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

    private void onCreateProject(EditorEvent e) throws Exception {
        long userId = ctx.getUserId();
        String name = e.get("name");

        CreateProjectRequest req =
                new CreateProjectRequest(userId, name);

        ProjectResponse res =
                api.post("/projects", req, ProjectResponse.class);

        long id = res.id();
        String pname = res.projectName();

        ProjectTreeItem item = new ProjectTreeItem(id, pname);
        ctx.getRootItem().getChildren().add(item);

        var pm = new ProjectModel();
        pm.id = id;
        pm.name = pname;
        pm.userId = userId;

        ctx.setProject(pm);
        ctx.setCurrentProjectNode(item);

        send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST)
                .with("projectNode", item));
    }


    private void onProjectSelected(EditorEvent e) {
        long pid = e.get("projectId");
        String pname = e.get("projectName");
        TreeItem<String> node = e.get("projectNode");

        var pm = new ProjectModel();
        pm.id = pid; pm.name = pname; pm.userId = ctx.getUserId();
        ctx.setProject(pm);
        ctx.setCurrentProjectNode(node);

        send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", node));
    }
}