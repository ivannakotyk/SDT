package com.ivanka.audioeditor.client.ui;

import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

public interface EditorContext {
    long getUserId();
    ProjectModel getProject();
    void setProject(ProjectModel pm);
    AudioProject getAudioProject();


    TreeItem<String> getRootItem();
    TreeView<String> getTree();
    VBox getTracksPane();
    void setCurrentProjectNode(TreeItem<String> node);
    TreeItem<String> getCurrentProjectNode();
    Stage getStage();
    String getActiveTrackName();
    void setActiveTrackName(String name);

    Map<Long, List<ProjectTrack>> getTrackCache();
    Map<String, Selection> getSelections();
    void setActiveTrackCursor(double frac);
    double getActiveTrackCursor();

    void drawWaveform(Canvas c, String trackName);
    void drawEmptyBackground(Canvas c, String msg);
    void redrawTrack(String trackName);
    void toast(String msg);
    void alertInfo(String msg);
    void alertWarn(String msg);
    void alertError(String msg);
    class Selection {
        public double xStart = -1;
        public double xEnd = -1;
        public void clear() { xStart = -1; xEnd = -1; }
        public boolean isActive() { return xStart >= 0 && xEnd >= 0 && Math.abs(xEnd - xStart) > 1.5; }
        public double left() { return Math.min(xStart, xEnd); }
        public double right() { return Math.max(xStart, xEnd); }
        public double width() {
            return Math.abs(xEnd - xStart);
        }
    }
}

