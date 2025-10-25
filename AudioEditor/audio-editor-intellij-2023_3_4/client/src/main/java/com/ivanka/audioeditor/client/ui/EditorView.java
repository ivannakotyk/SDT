package com.ivanka.audioeditor.client.ui;

import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.observers.*;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.sound.sampled.*;
import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EditorView implements EditorContext {
    private static final String FFMPEG = "ffmpeg";
    private final BorderPane root = new BorderPane();
    private final long userId;
    private ProjectModel project = new ProjectModel();
    private final TreeItem<String> rootItem = new TreeItem<>("Projects");
    private final TreeView<String> tree = new TreeView<>(rootItem);
    private final Map<Long, List<ProjectTrack>> trackCache = new java.util.HashMap<>();
    private final Map<String, TrackData> trackDataMap = new java.util.HashMap<>();
    private final Map<String, File> trackTempFiles = new java.util.HashMap<>();
    private TreeItem<String> currentProjectNode;
    private final VBox tracksPane = new VBox(15);
    private final Map<String, Selection> selections = new java.util.HashMap<>();
    private final Map<String, byte[]> clipboard = new java.util.HashMap<>();
    private final Stage stage;

    public EditorView(Stage stage, long userId) {
        this.stage = stage;
        this.userId = userId;

        root.setStyle("-fx-background-color: #101319; -fx-text-fill: white;");
        root.setPadding(new Insets(12));

        HBox top = new HBox(10);
        Button newProject = new Button("+ New Project");
        Button addTrack = new Button("+ Add Track");
        Button importAudio = new Button("Import Audio");
        Button exportAudio = new Button("Export Audio");
        Button refresh = new Button("Refresh");

        top.getChildren().addAll(newProject, addTrack, importAudio, exportAudio, refresh, new Separator());
        top.setAlignment(Pos.CENTER_LEFT);
        root.setTop(top);

        tree.setShowRoot(false);
        tree.setPrefWidth(260);
        rootItem.setExpanded(true);
        root.setLeft(tree);

        VBox center = new VBox(10);
        center.setPadding(new Insets(10));

        Label waveformLabel = new Label("Waveform View");
        waveformLabel.setTextFill(Color.WHITE);
        waveformLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        tracksPane.setPadding(new Insets(10));
        tracksPane.setBackground(new Background(new BackgroundFill(Color.web("#0b0f14"), new CornerRadii(10), Insets.EMPTY)));
        tracksPane.setBorder(new Border(new BorderStroke(Color.web("#1e2a38"), BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1))));

        ScrollPane scroll = new ScrollPane(tracksPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0b0f14; -fx-background-color: #0b0f14;");

        center.getChildren().addAll(waveformLabel, scroll);
        root.setCenter(center);

        var editor = AudioEditor.getInstance();
        editor.attach(new ProjectObserver(this));
        editor.attach(new TrackListObserver(this));
        editor.attach(new WaveformViewObserver(this));
        editor.attach(new PlaybackObserver(this));
        editor.attach(new ClipboardObserver(this));
        editor.attach(new ExportObserver(this));
        editor.attach(new ImportObserver(this));
        editor.attach(new NotificationObserver(this));

        newProject.setOnAction(e -> {
            Set<String> existingNames = rootItem.getChildren().stream()
                    .map(item -> item.getValue().replaceAll("\\s*|\\(id=.*\\)", "").trim())
                    .collect(Collectors.toSet());
            String suggestedName = generateUniqueName("My Project", existingNames);

            TextInputDialog d = new TextInputDialog(suggestedName);
            d.setHeaderText("Enter the new project name");
            d.setContentText("Name:");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) {
                    alertWarn("Project name cannot be empty.");
                    return;
                }
                editor.notifyObservers(
                        new EditorEvent(EditorEventType.PROJECT_CREATE_REQUEST).with("name", name)
                );
            });
        });

        addTrack.setOnAction(e -> {
            if (project.id == 0 || currentProjectNode == null) {
                alertWarn("Select or create a project first!");
                return;
            }
            Set<String> existingNames = currentProjectNode.getChildren().stream()
                    .map(item -> item.getValue().replaceAll("\\s*|—.*—", "").trim())
                    .collect(Collectors.toSet());
            String suggestedName = generateUniqueTrackName("Track", existingNames);
            TextInputDialog d = new TextInputDialog(suggestedName);
            d.setHeaderText("Enter the new track name");
            d.setContentText("Name:");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) {
                    alertWarn("Track name cannot be empty.");
                    return;
                }
                editor.notifyObservers(
                        new EditorEvent(EditorEventType.TRACK_ADD_REQUEST)
                                .with("projectId", project.id)
                                .with("trackName", name)
                );
            });
        });

        importAudio.setOnAction(e -> editor.notifyObservers(new EditorEvent(EditorEventType.IMPORT_REQUEST).with("stage", stage)));
        exportAudio.setOnAction(e -> editor.notifyObservers(new EditorEvent(EditorEventType.EXPORT_REQUEST).with("stage", stage)));
        refresh.setOnAction(e -> {
            if (currentProjectNode != null) {
                getTrackCache().remove(getProject().id);
                editor.notifyObservers(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", currentProjectNode));
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel == null || newSel == rootItem) return;
            String label = newSel.getValue();
            // Check based on content rather than emoji prefix
            if (label.contains("Track ") || label.contains("—")) {
                return;
            }
            if (label.contains("(id=")) {
                String idStr = label.replaceAll("[^0-9]", "");
                if (!idStr.isEmpty()) {
                    long pid = Long.parseLong(idStr);
                    String pname = label.replaceAll("\\s*|\\(id=.*\\)", "").trim();
                    editor.notifyObservers(new EditorEvent(EditorEventType.PROJECT_SELECTED)
                            .with("projectId", pid)
                            .with("projectName", pname)
                            .with("projectNode", newSel));
                }
            }
        });
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (currentProjectNode == null) return;
            String trackName = getActiveTrackName();
            if (trackName == null) return;

            if (e.isControlDown() && e.getCode() == KeyCode.C) {
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_COPY).with("trackName", trackName));
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_PASTE).with("trackName", trackName));
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || (e.isControlDown() && e.getCode() == KeyCode.X)) {
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_CUT).with("trackName", trackName));
                e.consume();
            }
        });

        drawPlaceholder();
    }
    private static String generateUniqueName(String baseName, Set<String> existingNames) {
        if (!existingNames.contains(baseName)) {
            return baseName;
        }
        String finalName;
        int counter = 2;
        do {
            finalName = baseName + " " + counter++;
        } while (existingNames.contains(finalName));
        return finalName;
    }
    private String generateUniqueTrackName(String baseName, Set<String> existingNames) {
        int i = 1;
        while (true) {
            String candidate = baseName + " " + i;
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
            i++;
        }
    }
    private void drawPlaceholder() {
        tracksPane.getChildren().clear();
        Label placeholder = new Label(" Select a project to view its tracks");
        placeholder.setTextFill(Color.web("#aaa"));
        tracksPane.getChildren().add(placeholder);
    }
    @Override
    public void redrawTrack(String trackName) {
        for (var node : tracksPane.getChildren()) {
            if (node instanceof VBox box) {
                if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
                    String lblName = lbl.getText().trim(); // Removed emoji replacement
                    if (lblName.equals(trackName)) {
                        if (box.getChildren().size() > 1 && box.getChildren().get(1) instanceof Canvas canvas) {
                            var td = trackDataMap.get(trackName);
                            var sel = selections.computeIfAbsent(trackName, k -> new Selection());
                            drawWaveform(canvas, td, sel);
                            return;
                        }
                    }
                }
            }
        }
    }
    public void drawWaveform(Canvas canvas, TrackData td, Selection sel) {
        if (td == null || td.pcm == null || td.pcm.length == 0) {
            drawEmptyBackground(canvas, "No audio data. Import a file to this track.");
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);

        drawTimeline(g, canvas.getWidth(), canvas.getHeight(), td.durationSec);

        g.setStroke(Color.web("#38bdf8"));
        double mid = canvas.getHeight() / 2.0;

        int width = (int) canvas.getWidth();
        if (width <= 0) return;

        int frameSize = td.frameSize > 0 ? td.frameSize : 2;
        if (frameSize <= 0) return;

        int totalFrames = td.pcm.length / frameSize;
        if (totalFrames <= 0) return;

        int framesPerPixel = Math.max(1, totalFrames / width);

        for (int x = 0; x < width; x++) {
            float min = 1.0f;
            float max = -1.0f;
            int startFrame = x * framesPerPixel;

            for (int i = 0; i < framesPerPixel; i++) {
                int frameIndex = startFrame + i;
                if (frameIndex >= totalFrames) break;
                int byteIndex = frameIndex * frameSize;
                if (byteIndex + 1 < td.pcm.length) {
                    short sample = (short) ((td.pcm[byteIndex + 1] << 8) | (td.pcm[byteIndex] & 0xFF));
                    float normalized = sample / 32768.0f;
                    if (normalized < min) min = normalized;
                    if (normalized > max) max = normalized;
                }
            }
            double yMax = mid - (max * (mid - 6));
            double yMin = mid - (min * (mid - 6));
            g.strokeLine(x, yMin, x, yMax);
        }
        if (sel != null && sel.isActive()) {
            g.setFill(Color.web("#22d3ee40"));
            g.fillRect(sel.left(), 0, sel.width(), canvas.getHeight());
            g.setStroke(Color.web("#06b6d4"));
            g.strokeRect(sel.left() + 0.5, 0.5, sel.width() - 1, canvas.getHeight() - 1);
        }
    }
    private void drawTimeline(GraphicsContext g, double width, double height, double durationSec) {
        g.setStroke(Color.web("#1e2a38"));
        for (int x = 0; (double) x < width; x += 40) g.strokeLine(x, 0, x, height);
        g.setFill(Color.web("#94a3b8"));
        g.fillText("0s", 6, 14);
        int marks = Math.max(1, (int) Math.floor(width / 100.0));
        for (int i = 1; i <= marks; i++) {
            double px = i * (width / marks);
            double sec = i * (durationSec / marks);
            g.fillText(String.format("%.1fs", sec), px - 10, 14);
        }
    }
    public void drawEmptyBackground(Canvas canvas, String msg) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);
        g.setStroke(Color.web("#1e2a38"));
        for (int x = 0; (double) x < canvas.getWidth(); x += 40) g.strokeLine(x, 0, x, canvas.getHeight());
        g.setFill(Color.web("#666"));
        g.fillText(msg, 12, 18);
    }
    public File ensureWav(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".wav")) return f;
        File tmp = File.createTempFile("import-", ".wav");
        List<String> cmd = List.of(FFMPEG, "-y", "-i", f.getAbsolutePath(), tmp.getAbsolutePath());
        int code = runProcess(cmd);
        if (code != 0) throw new RuntimeException("ffmpeg convert failed: exit " + code);
        return tmp;
    }
    public TrackData readWavToMemory(File wav) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, fmt.getSampleRate(), 16,
                    fmt.getChannels(), fmt.getChannels() * 2, fmt.getSampleRate(), false);
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(target, in)) {
                byte[] data = pcmStream.readAllBytes();
                TrackData td = new TrackData();
                td.format = target;
                td.pcm = data;
                td.frameSize = target.getFrameSize();
                td.frameRate = target.getFrameRate();
                td.framesCount = data.length / (long) td.frameSize;
                td.durationSec = td.framesCount > 0 && td.frameRate > 0 ? (double) td.framesCount / td.frameRate : 0.0;
                return td;
            }
        }
    }
    public File writeTrackTempWav(String trackName, TrackData td) throws Exception {
        File tmp = File.createTempFile("track-" + safe(trackName) + "-", ".wav");
        writePcmToWavFile(td.format, td.pcm, tmp);
        return tmp;
    }
    public File writePcmToTempWav(AudioFormat fmt, byte[] pcm) throws Exception {
        File tmp = File.createTempFile("seg-", ".wav");
        writePcmToWavFile(fmt, pcm, tmp);
        return tmp;
    }
    private void writePcmToWavFile(AudioFormat fmt, byte[] pcm, File out) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
             AudioInputStream ais = new AudioInputStream(bais, fmt, pcm.length / (long) fmt.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }
    private int runProcess(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) {}
        }
        return p.waitFor();
    }
    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }
    private String getActiveTrackName() {
        if (!tracksPane.getChildren().isEmpty()) {
            var firstNode = tracksPane.getChildren().get(0);
            if (firstNode instanceof VBox box) {
                if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
                    return lbl.getText().trim(); // Removed emoji replacement
                }
            }
        }
        return null;
    }
    public void alertWarn(String m) { new Alert(Alert.AlertType.WARNING, m).showAndWait(); }
    public void alertError(String m) { new Alert(Alert.AlertType.ERROR, m).showAndWait(); }
    public void alertInfo(String m) { new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }
    public void toast(String m) { System.out.println("ℹ " + m); }

    @Override public long getUserId() { return userId; }
    @Override public ProjectModel getProject() { return project; }
    @Override public void setProject(ProjectModel pm) { this.project = pm; }
    @Override public TreeItem<String> getRootItem() { return rootItem; }
    @Override public TreeView<String> getTree() { return tree; }
    @Override public VBox getTracksPane() { return tracksPane; }
    @Override public void setCurrentProjectNode(TreeItem<String> node) { this.currentProjectNode = node; }
    @Override public TreeItem<String> getCurrentProjectNode() { return currentProjectNode; }
    @Override public Map<Long, List<ProjectTrack>> getTrackCache() { return trackCache; }
    @Override public Map<String, TrackData> getTrackDataMap() { return trackDataMap; }
    @Override public Map<String, Selection> getSelections() { return selections; }
    @Override public Map<String, byte[]> getClipboard() { return clipboard; }
    @Override public Map<String, File> getTrackTempFiles() { return trackTempFiles; }
    @Override public Stage getStage() { return stage; }
    public Parent getRoot() { return root; }
}