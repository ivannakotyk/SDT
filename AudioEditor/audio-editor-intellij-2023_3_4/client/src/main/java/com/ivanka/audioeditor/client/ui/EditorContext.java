package com.ivanka.audioeditor.client.ui;

import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.List;
import java.util.Map;

public interface EditorContext {
    // Стан
    long getUserId();
    ProjectModel getProject();
    void setProject(ProjectModel pm);

    // UI вузли
    TreeItem<String> getRootItem();
    TreeView<String> getTree();
    VBox getTracksPane();
    void setCurrentProjectNode(TreeItem<String> node);
    TreeItem<String> getCurrentProjectNode();

    // Кеш/мапи
    Map<Long, List<ProjectTrack>> getTrackCache();
    Map<String, TrackData> getTrackDataMap();
    Map<String, Selection> getSelections();
    Map<String, byte[]> getClipboard();
    Map<String, File> getTrackTempFiles();

    // Малювання / утиліти
    void drawWaveform(Canvas c, TrackData td, Selection sel);
    void drawEmptyBackground(Canvas c, String msg);
    void redrawTrack(String trackName);
    void toast(String msg);
    void alertInfo(String msg);
    void alertWarn(String msg);
    void alertError(String msg);

    // IO
    TrackData readWavToMemory(File wav) throws Exception;
    File ensureWav(File any) throws Exception;
    File writeTrackTempWav(String trackName, TrackData td) throws Exception;
    File writePcmToTempWav(AudioFormat fmt, byte[] pcm) throws Exception;
    Stage getStage();

    class TrackData {
        public AudioFormat format;
        public byte[] pcm;
        public int frameSize;
        public float frameRate;
        public long framesCount;
        public double durationSec;
    }

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