package com.ivanka.audioeditor.client.ui.tree;

import javafx.scene.control.TreeItem;

public class ProjectTreeItem extends TreeItem<String> {

    private final long projectId;

    public ProjectTreeItem(long projectId, String name) {
        super(name);
        this.projectId = projectId;
    }

    public long getProjectId() {
        return projectId;
    }
}
