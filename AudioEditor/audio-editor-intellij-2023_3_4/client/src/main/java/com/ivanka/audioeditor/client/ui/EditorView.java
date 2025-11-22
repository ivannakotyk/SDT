package com.ivanka.audioeditor.client.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.ConcreteEditorMediator;
import com.ivanka.audioeditor.client.core.mediator.EditorMediator;
import com.ivanka.audioeditor.client.core.modules.*;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioSegment;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.model.composite.AudioComponent;
import com.ivanka.audioeditor.client.model.composite.PcmUtils;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.tree.ProjectTreeItem;
import com.ivanka.audioeditor.common.dto.FullProjectDTO;
import com.ivanka.audioeditor.common.dto.SegmentDTO;
import com.ivanka.audioeditor.common.dto.TrackDTO;
import javafx.application.Platform;
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

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class EditorView implements EditorContext {

    private static final String FFMPEG = "ffmpeg";
    private final BorderPane root = new BorderPane();
    private final long userId;
    private ProjectModel project = new ProjectModel();
    private AudioProject audioProject;
    private final ProjectTreeItem rootItem = new ProjectTreeItem(-1, "Projects");
    private final TreeView<String> tree = new TreeView<>(rootItem);
    private final Map<Long, List<ProjectTrack>> trackCache = new HashMap<>();
    private final Map<String, Selection> selectionsStore  = new HashMap<>();
    private final Map<Long, AudioProject> projectCache = new HashMap<>();
    private final Map<String, Selection> selections     = namespacedMap(() -> project.id, selectionsStore);
    private String activeTrackName = null;
    private TreeItem<String> currentProjectNode;
    private final VBox tracksPane = new VBox(15);
    private final Stage stage;
    private double activeTrackCursorFrac = 0.0;

    public EditorView(Stage stage, long userId) {
        this.stage = stage;
        this.userId = userId;

        root.setStyle("-fx-background-color: #101319; -fx-text-fill: white;");
        root.setPadding(new Insets(12));

        HBox top = new HBox(10);
        Button newProject = new Button("+ New Project");
        Button addTrack = new Button("+ Add Track");
        Button importAudio = new Button("Import Audio");
        Button exportAudio = new Button("Export Mix");
        Button saveProject = new Button("Save Project");
        saveProject.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold;");

        Button refresh = new Button("Refresh");
        Button playProject = new Button("Play Project");
        Button stopProject = new Button("Stop Project");

        playProject.setOnAction(ev -> playComposite());
        stopProject.setOnAction(ev -> stopComposite());
        saveProject.setOnAction(ev -> saveAllChanges());

        top.getChildren().addAll(newProject, saveProject, new Separator(), addTrack, importAudio, exportAudio, refresh, new Separator(), playProject, stopProject);
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
        EditorMediator mediator = new ConcreteEditorMediator();

        var projectModule       = new ProjectModule(this);
        var trackModule         = new TrackModule(this);
        var importModule        = new ImportModule(this);
        var waveformModule      = new WaveformModule(this);
        var playbackModule      = new PlaybackModule(this);
        var exportModule        = new ExportModule(this);
        var notificationModule  = new NotificationModule(this);
        var clipboardModule     = new ClipboardModule(this);

        mediator.register(projectModule.key(),       projectModule);
        mediator.register(trackModule.key(),         trackModule);
        mediator.register(importModule.key(),        importModule);
        mediator.register(waveformModule.key(),      waveformModule);
        mediator.register(playbackModule.key(),      playbackModule);
        mediator.register(exportModule.key(),        exportModule);
        mediator.register(notificationModule.key(),  notificationModule);
        mediator.register(clipboardModule.key(),     clipboardModule);
        editor.attach(new com.ivanka.audioeditor.client.core.observers.MediatorObserver(mediator));

        newProject.setOnAction(e -> {
            Set<String> existingNames = rootItem.getChildren().stream()
                    .map(item -> item.getValue().trim())
                    .collect(Collectors.toSet());
            String suggestedName = generateUniqueName("My Project", existingNames);
            TextInputDialog d = new TextInputDialog(suggestedName);
            d.setHeaderText("Enter the new project name");
            d.setContentText("Name:");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) { alertWarn("Project name cannot be empty."); return; }
                editor.notifyObservers(new EditorEvent(EditorEventType.PROJECT_CREATE_REQUEST).with("name", name.trim()));
            });
        });

        addTrack.setOnAction(e -> {
            if (project.id == 0 || currentProjectNode == null) { alertWarn("Select or create a project first!"); return; }
            editor.notifyObservers(new EditorEvent(EditorEventType.TRACK_ADD_REQUEST).with("projectId", project.id).with("trackName", "Track " + (tracksPane.getChildren().size()+1)));
        });

        importAudio.setOnAction(e -> editor.notifyObservers(new EditorEvent(EditorEventType.IMPORT_REQUEST).with("stage", stage)));
        exportAudio.setOnAction(e -> editor.notifyObservers(new EditorEvent(EditorEventType.EXPORT_REQUEST).with("stage", stage)));
        refresh.setOnAction(e -> { if (currentProjectNode != null) hydrateProjectFromServer(project.id); });

        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel == null || newSel == rootItem) return;
            if (!(newSel instanceof ProjectTreeItem pItem)) return;
            editor.notifyObservers(new EditorEvent(EditorEventType.PROJECT_SELECTED)
                    .with("projectId", pItem.getProjectId())
                    .with("projectName", pItem.getValue())
                    .with("projectNode", pItem));
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (currentProjectNode == null) return;
            String trackName = getActiveTrackName();
            if (trackName == null) return;

            if (e.isControlDown() && e.getCode() == KeyCode.C) {
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_COPY).with("projectId", project.id).with("trackName", trackName)); e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
                setActiveTrackName(trackName);
                double frac = getActiveTrackCursor();
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_PASTE).with("projectId", project.id).with("trackName", trackName).with("cursorFrac", frac)); e.consume();
            } else if (e.getCode() == KeyCode.DELETE || (e.isControlDown() && e.getCode() == KeyCode.X)) {
                editor.notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_CUT).with("projectId", project.id).with("trackName", trackName)); e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.S) {
                saveAllChanges(); e.consume();
            }
        });

        drawPlaceholder();
    }

    private void saveAllChanges() {
        if (audioProject == null) return;
        toast("Saving changes to server...");
        CompletableFuture.runAsync(() -> {
            try {
                var api = ApiClient.getInstance();
                int savedCount = 0;
                for (AudioComponent t : audioProject.getChildren()) {
                    if (t instanceof AudioTrack track) {
                        for (AudioComponent s : track.getChildren()) {
                            if (s instanceof AudioSegment seg) {
                                if (seg.getId() > 0) {
                                    File temp = File.createTempFile("save-" + seg.getName(), ".wav");
                                    seg.exportTo(temp, "wav");
                                    api.postMultipart("/segments/" + seg.getId() + "/upload", Map.of(), temp);
                                    temp.delete();
                                    savedCount++;
                                }
                            }
                        }
                    }
                }
                final int c = savedCount;
                Platform.runLater(() -> alertInfo("Project saved successfully!\nUpdated " + c + " segments."));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> alertError("Save failed: " + e.getMessage()));
            }
        });
    }

    public void hydrateProjectFromServer(long projectId) {
        toast("Restoring project data from server...");

        CompletableFuture.runAsync(() -> {
            try {
                var api = ApiClient.getInstance();
                var mapper = new ObjectMapper();

                String json = api.get("/projects/" + projectId);
                FullProjectDTO dto = mapper.readValue(json, FullProjectDTO.class);

                String projName = dto.projectName();
                AudioProject reconstructed = new AudioProject(projName);
                List<ProjectTrack> uiTracks = new ArrayList<>();

                if (dto.tracks() != null) {
                    for (TrackDTO tDto : dto.tracks()) {
                        String tName = tDto.name();
                        long tId = tDto.id();
                        int tOrder = tDto.order();

                        AudioTrack audioTrack = new AudioTrack(tName);
                        uiTracks.add(new ProjectTrack(tId, tName, tOrder));

                        if (tDto.segments() != null) {
                            for (SegmentDTO sDto : tDto.segments()) {
                                String fullPath = sDto.wavPath();
                                if (fullPath != null && !fullPath.isBlank()) {
                                    String filename = sDto.name();
                                    File tempWav = File.createTempFile("restored-", "_" + filename);
                                    try {
                                        api.downloadFile(fullPath, tempWav);
                                        AudioFormat[] fmt = new AudioFormat[1];
                                        float[][] samples = PcmUtils.readWavStereo(tempWav, fmt);
                                        AudioSegment seg = new AudioSegment(filename, samples, fmt[0]);
                                        seg.setId(sDto.id());
                                        audioTrack.add(seg);
                                        System.out.println("Restored segment: " + filename + " (ID: " + sDto.id() + ")");
                                    } catch (Exception ex) {
                                        System.err.println("Failed to download segment: " + filename);
                                    }
                                }
                            }
                        }
                        reconstructed.add(audioTrack);
                    }
                }

                Platform.runLater(() -> {
                    this.audioProject = reconstructed;
                    projectCache.put(projectId, reconstructed);
                    trackCache.put(projectId, uiTracks);
                    AudioEditor.getInstance().notifyObservers(
                            new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST)
                                    .with("projectNode", currentProjectNode));
                    toast("Project restored successfully!");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> alertError("Failed to restore project: " + e.getMessage()));
            }
        });
    }

    public void playComposite() {
        try {
            if (audioProject != null) {
                CompletableFuture.runAsync(() -> {
                    try { audioProject.play(); } catch (Exception e) {
                        e.printStackTrace();
                        Platform.runLater(() -> alertError("Playback error: " + e.getMessage()));
                    }
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            alertError("Failed to start playback thread.");
        }
    }
    public void stopComposite() { try { if (audioProject != null) audioProject.stop(); } catch (Exception ex) { ex.printStackTrace(); } }

    @Override public void redrawTrack(String trackName) {
        for (var node : tracksPane.getChildren()) {
            if (node instanceof VBox box) {
                if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
                    if (lbl.getText().equals(trackName) && box.getChildren().size() > 1 && box.getChildren().get(1) instanceof Canvas c) {
                        drawWaveform(c, trackName);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void drawWaveform(Canvas canvas, String trackName) {
        AudioSegment seg = getMainSegment(trackName);
        var sel = selections.computeIfAbsent(trackName, k -> new Selection());

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);
        g.setStroke(Color.web("#38bdf8"));
        double mid = canvas.getHeight() / 2.0;

        if (seg == null) {
            drawEmptyBackground(canvas, "No audio data.");
            return;
        }
        float[][] samples = seg.getSamples();
        if (samples == null || samples.length == 0 || samples[0].length == 0) {
            drawEmptyBackground(canvas, "Empty.");
            return;
        }
        float[] leftChannel = samples[0];
        int totalSamples = leftChannel.length;
        int width = (int) canvas.getWidth();
        int samplesPerPixel = Math.max(1, totalSamples / width);

        for (int x = 0; x < width; x++) {
            float min = 1.0f;
            float max = -1.0f;
            int startSample = x * samplesPerPixel;
            for (int i = 0; i < samplesPerPixel; i++) {
                int idx = startSample + i;
                if (idx >= totalSamples) break;
                float val = leftChannel[idx];
                if (val < min) min = val;
                if (val > max) max = val;
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

    @Override public void drawEmptyBackground(Canvas canvas, String msg) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);
        g.setStroke(Color.web("#1e2a38"));
        for (int x = 0; x < canvas.getWidth(); x += 40) g.strokeLine(x, 0, x, canvas.getHeight());
        g.setFill(Color.web("#666"));
        g.fillText(msg, 12, 18);
    }

    public File ensureWav(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".wav")) return f;
        File tmp = File.createTempFile("import-", ".wav");
        List<String> cmd = List.of(FFMPEG, "-y", "-i", f.getAbsolutePath(), tmp.getAbsolutePath());
        int code = runProcess(cmd);
        if (code != 0) throw new RuntimeException("ffmpeg error: " + code);
        return tmp;
    }
    private int runProcess(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) { while (br.readLine() != null) {} }
        return p.waitFor();
    }

    // Гетери/Сетери без змін
    @Override public String getActiveTrackName() { return activeTrackName; }
    @Override public void setActiveTrackName(String name) { this.activeTrackName = name; this.activeTrackCursorFrac = 0.0; }
    @Override public void setActiveTrackCursor(double frac) { this.activeTrackCursorFrac = frac; }
    @Override public double getActiveTrackCursor() { return this.activeTrackCursorFrac; }
    @Override public long getUserId() { return userId; }
    @Override public ProjectModel getProject() { return project; }
    @Override
    public void setProject(ProjectModel pm) {
        this.project = pm;
        if (projectCache.containsKey(pm.id)) {
            this.audioProject = projectCache.get(pm.id);
            System.out.println("Project loaded from cache: " + pm.name);
        } else {
            this.audioProject = new AudioProject(pm.name);
            projectCache.put(pm.id, this.audioProject);
        }
    }
    @Override public ProjectTreeItem getRootItem() { return rootItem; }
    @Override public TreeView<String> getTree() { return tree; }
    @Override public VBox getTracksPane() { return tracksPane; }
    @Override public void setCurrentProjectNode(TreeItem<String> node) { this.currentProjectNode = node; }
    @Override public TreeItem<String> getCurrentProjectNode() { return currentProjectNode; }
    @Override public Map<Long, List<ProjectTrack>> getTrackCache() { return trackCache; }
    @Override public Map<String, Selection> getSelections() { return selections; }
    @Override public Stage getStage() { return stage; }
    @Override public AudioProject getAudioProject() { return audioProject; }
    public Parent getRoot() { return root; }
    @Override public void toast(String m) { System.out.println("ℹ " + m); }
    @Override public void alertInfo(String m) { new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }
    @Override public void alertWarn(String m) { new Alert(Alert.AlertType.WARNING, m).showAndWait(); }
    @Override public void alertError(String m) { new Alert(Alert.AlertType.ERROR, m).showAndWait(); }

    private String generateUniqueName(String requested, Set<String> existingNames) {
        String base = requested.trim(); String prefix = base; int startNumber = 1;
        var m = java.util.regex.Pattern.compile("^(.*?)(\\s(\\d+))$").matcher(base);
        if (m.matches()) { prefix = m.group(1); startNumber = Integer.parseInt(m.group(3)); }
        if (!existingNames.contains(base)) return base;
        int n = startNumber + 1; String candidate;
        do { candidate = prefix + " " + n++; } while (existingNames.contains(candidate));
        return candidate;
    }
    private void drawPlaceholder() { tracksPane.getChildren().clear(); tracksPane.getChildren().add(new Label("Select project")); }
    private static <V> Map<String, V> namespacedMap(Supplier<Long> s, Map<String, V> b) { return b; }
    private AudioTrack getTrack(String name) { if(audioProject==null) return null; return (AudioTrack)audioProject.getChildren().stream().filter(c->c.getName().equals(name)).findFirst().orElse(null); }
    private AudioSegment getMainSegment(String name) { AudioTrack t = getTrack(name); return (t!=null && !t.getChildren().isEmpty()) ? (AudioSegment)t.getChildren().get(0) : null; }
}