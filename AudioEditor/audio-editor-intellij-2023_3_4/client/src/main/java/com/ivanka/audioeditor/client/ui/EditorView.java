package com.ivanka.audioeditor.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.model.ProjectModel;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class EditorView {
    private static final String FFMPEG = "ffmpeg";
    private static final double DEFAULT_SAMPLE_ZOOM = 1.0;
    private static final double MAX_ZOOM = 8.0;
    private static final double MIN_ZOOM = 0.25;

    private final BorderPane root = new BorderPane();

    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private final long userId;
    private ProjectModel project = new ProjectModel();

    private final TreeItem<String> rootItem = new TreeItem<>("Projects");
    private final TreeView<String> tree = new TreeView<>(rootItem);
    private final Map<Long, List<ProjectTrack>> trackCache = new HashMap<>();

    private static class TrackData {
        AudioFormat format;
        byte[] pcm;
        int frameSize;
        float frameRate;
        long framesCount;
        double durationSec;
    }
    private final Map<String, TrackData> trackDataMap = new HashMap<>();

    private final Map<String, File> trackTempFiles = new HashMap<>();

    private Clip currentClip;

    private TreeItem<String> currentProjectNode;
    private final VBox tracksPane = new VBox(15);

    private static class Selection {
        double xStart = -1;
        double xEnd = -1;
        void clear() { xStart = -1; xEnd = -1; }
        boolean isActive() { return xStart >= 0 && xEnd >= 0 && Math.abs(xEnd - xStart) > 1.5; }
        double left() { return Math.min(xStart, xEnd); }
        double right() { return Math.max(xStart, xEnd); }
    }
    private final Map<String, Selection> selections = new HashMap<>();
    private final Map<String, byte[]> clipboard = new HashMap<>();
    private double zoom = DEFAULT_SAMPLE_ZOOM;

    public EditorView(Stage stage, long userId) {
        this.userId = userId;
        root.setStyle("-fx-background-color: #101319; -fx-text-fill: white;");
        root.setPadding(new Insets(12));

        // Верхня панель
        HBox top = new HBox(10);
        Button newProject = new Button("+ New Project");
        Button addTrack = new Button("+ Add Track");
        Button importAudio = new Button("Import Audio");
        Button exportAudio = new Button("Export Audio");
        Button refresh = new Button("Refresh");

        // Зум
        Slider zoomSlider = new Slider(MIN_ZOOM, MAX_ZOOM, zoom);
        zoomSlider.setPrefWidth(160);
        Label zoomLbl = new Label("Zoom");
        zoomLbl.setTextFill(Color.WHITE);

        top.getChildren().addAll(newProject, addTrack, importAudio, exportAudio, refresh, new Separator(), zoomLbl, zoomSlider);
        top.setAlignment(Pos.CENTER_LEFT);
        root.setTop(top);

        // Ліва частина
        tree.setShowRoot(false);
        tree.setPrefWidth(260);
        rootItem.setExpanded(true);
        root.setLeft(tree);

        // Центральна частина
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

        // ==== Дії кнопок ====

        newProject.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog("My Project");
            d.setHeaderText("Project name");
            d.setContentText("Name:");
            d.showAndWait().ifPresent(name -> {
                try {
                    var newProj = AudioEditor.getInstance().createNewProject(userId, name);
                    project.id = newProj.id;
                    project.name = newProj.projectName;
                    TreeItem<String> projNode = new TreeItem<>("🎧 " + newProj.projectName + " (id=" + newProj.id + ")");
                    projNode.setExpanded(true);
                    rootItem.getChildren().add(projNode);
                    currentProjectNode = projNode;
                    refreshTracks(projNode);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    alertError("Failed to create project: " + ex.getMessage());
                }
            });
        });

        addTrack.setOnAction(e -> {
            if (project.id == 0 || currentProjectNode == null) {
                alertWarn("Select or create a project first!");
                return;
            }
            TextInputDialog d = new TextInputDialog("Track " + (currentProjectNode.getChildren().size() + 1));
            d.setHeaderText("Track name");
            d.setContentText("Name:");
            d.showAndWait().ifPresent(name -> {
                try {
                    System.out.println("📤 Adding track to project ID = " + project.id);
                    String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
                    api.postForm("/projects/" + project.id + "/tracks", "name=" + encodedName);
                    TreeItem<String> newTrackNode = new TreeItem<>("🎵 " + name);
                    currentProjectNode.getChildren().add(newTrackNode);
                    drawTrackUI(name);
                    tracksPane.requestLayout();
                    trackCache.computeIfAbsent(project.id, k -> new ArrayList<>()).add(new ProjectTrack(0, name, 0));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });

        importAudio.setOnAction(e -> {
            if (project.id == 0) return;
            try {
                FileChooser fc = new FileChooser();
                fc.setTitle("Select Audio File");
                fc.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.ogg", "*.flac")
                );
                File f = fc.showOpenDialog(stage);
                if (f == null) return;

                List<String> trackNames = tracksPane.getChildren().stream()
                        .filter(node -> node instanceof VBox)
                        .map(node -> ((Label) ((VBox) node).getChildren().get(0)).getText().replace("🎶 ", ""))
                        .toList();

                if (trackNames.isEmpty()) {
                    alertWarn("No tracks to import into! Create a track first.");
                    return;
                }

                ChoiceDialog<String> dialog = new ChoiceDialog<>(trackNames.get(0), trackNames);
                dialog.setHeaderText("Select track to import audio");
                dialog.setContentText("Track:");
                dialog.showAndWait().ifPresent(trackName -> {
                    try {
                        File wav = ensureWav(f);
                        TrackData td = readWavToMemory(wav);
                        trackDataMap.put(trackName, td);
                        File tmp = writeTrackTempWav(trackName, td);
                        trackTempFiles.put(trackName, tmp);
                        System.out.println("🎵 Imported " + f.getName() + " -> track " + trackName);

                        for (var node : tracksPane.getChildren()) {
                            if (node instanceof VBox box) {
                                Label lbl = (Label) box.getChildren().get(0);
                                if (lbl.getText().equals("🎶 " + trackName)) {
                                    Canvas canvas = (Canvas) box.getChildren().get(1);
                                    drawWaveform(canvas, td, selections.computeIfAbsent(trackName, k -> new Selection()));
                                }
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        alertError("Import failed: " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        exportAudio.setOnAction(e -> {
            try {
                if (rootItem.getChildren().isEmpty()) {
                    alertWarn("No projects available to export!");
                    return;
                }

                // --- КРОК 1: вибір проекту ---
                List<String> projectNames = rootItem.getChildren().stream()
                        .map(TreeItem::getValue)
                        .filter(v -> v.startsWith("🎧"))
                        .toList();

                ChoiceDialog<String> projDlg = new ChoiceDialog<>(projectNames.get(0), projectNames);
                projDlg.setHeaderText("Select project for export");
                projDlg.setContentText("Project:");
                Optional<String> projSel = projDlg.showAndWait();
                if (projSel.isEmpty()) return;

                String projectLabel = projSel.get();
                long selectedProjectId = Long.parseLong(projectLabel.replaceAll("[^0-9]", ""));
                String selectedProjectName = projectLabel.replaceAll("🎧\\s*|\\(id=.*\\)", "").trim();

                // --- КРОК 2: вибір доріжки ---
                List<String> trackNames = trackTempFiles.keySet().stream().toList();
                if (trackNames.isEmpty()) {
                    alertWarn("No audio tracks imported to export!");
                    return;
                }

                ChoiceDialog<String> trackDlg = new ChoiceDialog<>(trackNames.get(0), trackNames);
                trackDlg.setHeaderText("Select track to export");
                trackDlg.setContentText("Track:");
                Optional<String> trackSel = trackDlg.showAndWait();
                if (trackSel.isEmpty()) return;

                String selectedTrack = trackSel.get();
                File wavIn = trackTempFiles.get(selectedTrack);
                if (wavIn == null || !wavIn.exists()) {
                    alertError("Selected track has no audio file!");
                    return;
                }

                // --- КРОК 3: вибір формату ---
                ChoiceDialog<String> fmtDlg = new ChoiceDialog<>("mp3", List.of("mp3", "ogg", "flac", "wav"));
                fmtDlg.setHeaderText("Select export format");
                fmtDlg.setContentText("Format:");
                Optional<String> fmtSel = fmtDlg.showAndWait();
                if (fmtSel.isEmpty()) return;
                String format = fmtSel.get();

                // --- КРОК 4: надсилання на сервер ---
                try {
                    String response = api.postMultipart("/export", Map.of(
                            "userId", String.valueOf(userId),
                            "projectId", String.valueOf(selectedProjectId),
                            "trackName", selectedTrack,
                            "format", format
                    ), wavIn);

                    Map<String, Object> res = mapper.readValue(response, new TypeReference<>() {});
                    String path = res.get("path").toString();

                    alertInfo("✅ Exported successfully!\nProject: " + selectedProjectName +
                            "\nTrack: " + selectedTrack + "\nFormat: " + format +
                            "\nSaved on server:\n" + path);

                    // --- Автоматичне відкриття ---
                    try {
                        File exportedFile = new File(path);
                        if (exportedFile.exists()) {
                            java.awt.Desktop.getDesktop().open(exportedFile);
                        } else {
                            File exportDir = new File("E:\\3 Курс\\ТРПЗ\\AudioEditor\\audio-editor-intellij-2023_3_4\\server\\storage\\exports");
                            if (exportDir.exists()) java.awt.Desktop.getDesktop().open(exportDir);
                        }
                    } catch (Exception openEx) {
                        System.err.println("⚠ Не вдалося відкрити файл: " + openEx.getMessage());
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    alertError("Export failed: " + ex.getMessage());
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        refresh.setOnAction(e -> {
            if (currentProjectNode != null) refreshTracks(currentProjectNode);
        });

        zoomSlider.valueProperty().addListener((obs, o, n) -> {
            zoom = n.doubleValue();
            for (var node : tracksPane.getChildren()) {
                if (node instanceof VBox box) {
                    Label lbl = (Label) box.getChildren().get(0);
                    String trackName = lbl.getText().replace("🎶 ", "");
                    TrackData td = trackDataMap.get(trackName);
                    if (td != null) {
                        Canvas canvas = (Canvas) box.getChildren().get(1);
                        drawWaveform(canvas, td, selections.computeIfAbsent(trackName, k -> new Selection()));
                    } else {
                        Canvas canvas = (Canvas) box.getChildren().get(1);
                        drawEmptyBackground(canvas, "No audio");
                    }
                }
            }
        });

        // ==== Обробка кліків у дереві ====
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel == null) return;
            String label = newSel.getValue();
            if (label.startsWith("🎵")) {
                tracksPane.getChildren().clear();
                drawSingleTrack(label);
                return;
            }
            if (label.startsWith("🎧")) {
                String idStr = label.replaceAll("[^0-9]", "");
                if (!idStr.isEmpty()) {
                    long pid = Long.parseLong(idStr);
                    project = new ProjectModel();
                    project.id = pid;
                    project.name = label.replaceAll("🎧\\s*|\\(id=.*\\)", "").trim();
                    currentProjectNode = newSel;
                    tracksPane.getChildren().clear();
                    refreshTracks(newSel);
                }
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (currentProjectNode == null) return;
            String trackName = getActiveTrackName();
            if (trackName == null) return;

            if (e.isControlDown() && e.getCode() == KeyCode.C) {
                doCopy(trackName);
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
                doPaste(trackName);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || (e.isControlDown() && e.getCode() == KeyCode.X)) {
                doCut(trackName);
                e.consume();
            }
        });

        drawPlaceholder();
    }

    // ======= допоміжні UI-частини =======

    private void refreshTracks(TreeItem<String> projectNode) {
        try {
            if (currentProjectNode != projectNode) {
                tracksPane.getChildren().clear();
                currentProjectNode = projectNode;
            }

            List<ProjectTrack> tracks;
            if (trackCache.containsKey(project.id)) {
                tracks = trackCache.get(project.id);
            } else {
                String res = api.get("/tracks/by-project/" + project.id + "?nocache=" + System.nanoTime());
                tracks = mapper.readValue(res, new TypeReference<>() {});
                trackCache.put(project.id, tracks);
            }

            projectNode.getChildren().clear();
            if (tracks.isEmpty()) {
                projectNode.getChildren().add(new TreeItem<>("— No tracks yet —"));
                drawPlaceholder();
                return;
            }

            for (ProjectTrack t : tracks) {
                TreeItem<String> trackNode = new TreeItem<>("🎵 " + t.trackName + " (id=" + t.id + ")");
                projectNode.getChildren().add(trackNode);
                drawTrackUI(t.trackName);
            }
            projectNode.setExpanded(true);
            tracksPane.requestLayout();

        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void drawTrackUI(String name) {
        Label trackLabel = new Label("🎶 " + name);
        trackLabel.setTextFill(Color.web("#7dd3fc"));
        trackLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Canvas canvas = new Canvas(900, 140);

        // панель керування (play/stop + редагування)
        Button play = new Button("▶");
        Button stop = new Button("■");
        Button copy = new Button("Copy");
        Button paste = new Button("Paste");
        Button cut = new Button("Cut");
        Button reverse = new Button("Reverse");
        Button speedUp = new Button("Speed x1.25");
        Button slowDown = new Button("Speed x0.75");

        HBox controls = new HBox(8, play, stop, new Separator(), copy, paste, cut, new Separator(), reverse, speedUp, slowDown);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, trackLabel, canvas, controls);
        box.setPadding(new Insets(8));
        box.setSpacing(6);
        box.setStyle("""
            -fx-background-color: #101319;
            -fx-border-color: #38bdf8;
            -fx-border-width: 1.5;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-effect: dropshadow(gaussian, rgba(56,189,248,0.25), 6, 0.2, 0, 0);
        """);

        tracksPane.getChildren().add(box);

        // початковий малюнок
        TrackData td = trackDataMap.get(name);
        if (td == null) {
            drawEmptyBackground(canvas, "No audio");
        } else {
            drawWaveform(canvas, td, selections.computeIfAbsent(name, k -> new Selection()));
        }

        // мишка для вибору сегмента
        Selection sel = selections.computeIfAbsent(name, k -> new Selection());
        canvas.setOnMousePressed(e -> {
            sel.xStart = clamp(e.getX(), 0, canvas.getWidth());
            sel.xEnd = sel.xStart;
            drawWaveform(canvas, trackDataMap.get(name), sel);
        });
        canvas.setOnMouseDragged(e -> {
            sel.xEnd = clamp(e.getX(), 0, canvas.getWidth());
            drawWaveform(canvas, trackDataMap.get(name), sel);
        });
        canvas.setOnMouseReleased(e -> {
            sel.xEnd = clamp(e.getX(), 0, canvas.getWidth());
            drawWaveform(canvas, trackDataMap.get(name), sel);
        });

        play.setOnAction(e -> playTrack(name));
        stop.setOnAction(e -> stopPlayback());

        copy.setOnAction(e -> doCopy(name));
        paste.setOnAction(e -> doPaste(name));
        cut.setOnAction(e -> doCut(name));
        reverse.setOnAction(e -> doReverse(name, canvas));
        speedUp.setOnAction(e -> doTempo(name, canvas, 1.25));
        slowDown.setOnAction(e -> doTempo(name, canvas, 0.75));
    }

    private void drawSingleTrack(String label) {
        tracksPane.getChildren().clear();
        drawTrackUI(label.replace("🎵 ", "").replaceAll("\\s*\\(id=.*\\)", "").trim());
    }

    private void drawPlaceholder() {
        tracksPane.getChildren().clear();
        Label placeholder = new Label("🎵 Select a project or track to view");
        placeholder.setTextFill(Color.web("#aaa"));
        tracksPane.getChildren().add(placeholder);
    }

    // ======= аудіо / візуалізація =======

    private void drawEmptyBackground(Canvas canvas, String msg) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);
        g.setStroke(Color.web("#1e2a38"));
        for (int x = 0; x < canvas.getWidth(); x += 40) g.strokeLine(x, 0, x, canvas.getHeight());

        g.setFill(Color.web("#666"));
        g.fillText(msg, 12, 18);
    }

    private void drawWaveform(Canvas canvas, TrackData td, Selection sel) {
        if (td == null) { drawEmptyBackground(canvas, "No audio"); return; }

        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.web("#0b0f14"));
        g.fillRoundRect(0, 0, canvas.getWidth(), canvas.getHeight(), 16, 16);

        drawTimeline(g, canvas.getWidth(), canvas.getHeight(), td.durationSec);

        g.setStroke(Color.web("#38bdf8"));
        double mid = canvas.getHeight() / 2.0;

        int width = (int) canvas.getWidth();
        int totalBytes = td.pcm.length;
        int step = Math.max(1, (int) ((totalBytes / (double) width) / zoom));

        for (int x = 0; x < width; x++) {
            int i = x * step;
            if (i >= totalBytes) break;
            int sample = td.pcm[i];
            double amp = (sample / 128.0) * (canvas.getHeight() / 2.0 - 6);
            g.strokeLine(x, mid - amp, x, mid + amp);
        }

        if (sel != null && sel.isActive()) {
            g.setFill(Color.web("#22d3ee40"));
            g.fillRect(sel.left(), 0, sel.right() - sel.left(), canvas.getHeight());
            g.setStroke(Color.web("#06b6d4"));
            g.strokeRect(sel.left() + 0.5, 0.5, (sel.right() - sel.left()) - 1, canvas.getHeight() - 1);
        }
    }

    private void drawTimeline(GraphicsContext g, double width, double height, double durationSec) {
        g.setStroke(Color.web("#1e2a38"));
        for (int x = 0; x < width; x += 40) g.strokeLine(x, 0, x, height);

        g.setFill(Color.web("#94a3b8"));
        g.fillText("0s", 6, 14);
        int marks = Math.max(1, (int) Math.floor(width / 100.0));
        for (int i = 1; i <= marks; i++) {
            double px = i * (width / marks);
            double sec = i * (durationSec / marks);
            g.fillText(String.format("%.1fs", sec), px - 10, 14);
        }
    }

    private void playTrack(String trackName) {
        try {
            stopPlayback();
            TrackData td = trackDataMap.get(trackName);
            if (td != null) {
                File tmp = writeTrackTempWav(trackName, td);
                trackTempFiles.put(trackName, tmp);
            }
            File wavFile = trackTempFiles.get(trackName);
            if (wavFile == null || !wavFile.exists()) {
                alertWarn("No audio file imported for this track!");
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            currentClip.start();
            currentClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    try { currentClip.close(); } catch (Exception ignored) {}
                }
            });
        } catch (UnsupportedAudioFileException ex) {
            alertError("Unsupported audio format (use WAV PCM)");
        } catch (Exception ex) {
            ex.printStackTrace();
            alertError("Cannot play audio: " + ex.getMessage());
        }
    }

    private void stopPlayback() {
        if (currentClip != null && currentClip.isOpen()) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
    }

    // ======= редагування (Copy/Paste/Cut/Reverse/Tempo) =======

    private void doCopy(String trackName) {
        var td = trackDataMap.get(trackName);
        var sel = selections.get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        int[] range = selectionToByteRange(sel, td, 900);
        clipboard.put(trackName, Arrays.copyOfRange(td.pcm, range[0], range[1]));
        toast("Copied " + (range[1] - range[0]) + " bytes");
    }

    private void doCut(String trackName) {
        var td = trackDataMap.get(trackName);
        var sel = selections.get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        int[] range = selectionToByteRange(sel, td, 900);
        clipboard.put(trackName, Arrays.copyOfRange(td.pcm, range[0], range[1]));

        byte[] left = Arrays.copyOfRange(td.pcm, 0, range[0]);
        byte[] right = Arrays.copyOfRange(td.pcm, range[1], td.pcm.length);
        td.pcm = concat(left, right);
        td.framesCount = td.pcm.length / td.frameSize;
        td.durationSec = td.framesCount / td.frameRate;

        redrawTrack(trackName);
        sel.clear();
        toast("Cut " + (range[1] - range[0]) + " bytes");
    }

    private void doPaste(String trackName) {
        var td = trackDataMap.get(trackName);
        var sel = selections.get(trackName);
        var clip = clipboard.get(trackName);
        if (td == null || clip == null) { toast("Clipboard empty"); return; }

        int insertAt;
        if (sel != null && (sel.xStart >= 0)) {
            int[] range = selectionToByteRange(new Selection(){{
                xStart = sel.xStart; xEnd = sel.xStart + 1;
            }}, td, 900);
            insertAt = range[0];
        } else {
            insertAt = td.pcm.length; // вкінці
        }

        byte[] left = Arrays.copyOfRange(td.pcm, 0, insertAt);
        byte[] right = Arrays.copyOfRange(td.pcm, insertAt, td.pcm.length);
        td.pcm = concat(left, clip, right);
        td.framesCount = td.pcm.length / td.frameSize;
        td.durationSec = td.framesCount / td.frameRate;

        redrawTrack(trackName);
        toast("Pasted " + clip.length + " bytes");
    }

    private void doReverse(String trackName, Canvas canvas) {
        var td = trackDataMap.get(trackName);
        var sel = selections.get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        int[] range = selectionToByteRange(sel, td, canvas.getWidth());
        reverseInPlaceByFrames(td.pcm, range[0], range[1], td.frameSize);

        redrawTrack(trackName);
        toast("Reversed segment");
    }

    private void doTempo(String trackName, Canvas canvas, double atempo) {
        var td = trackDataMap.get(trackName);
        var sel = selections.get(trackName);
        if (td == null || sel == null || !sel.isActive()) return;

        try {
            int[] range = selectionToByteRange(sel, td, canvas.getWidth());
            File inWav = writePcmToTempWav(td.format, Arrays.copyOfRange(td.pcm, range[0], range[1]));
            File outWav = File.createTempFile("seg-tempo-", ".wav");
            List<String> cmd = List.of(
                    FFMPEG, "-y", "-i", inWav.getAbsolutePath(),
                    "-filter:a", "atempo=" + atempo,
                    outWav.getAbsolutePath()
            );
            int code = runProcess(cmd);
            if (code != 0) throw new RuntimeException("ffmpeg atempo failed: exit " + code);

            TrackData segNew = readWavToMemory(outWav);
            byte[] left = Arrays.copyOfRange(td.pcm, 0, range[0]);
            byte[] right = Arrays.copyOfRange(td.pcm, range[1], td.pcm.length);
            td.pcm = concat(left, segNew.pcm, right);
            td.framesCount = td.pcm.length / td.frameSize;
            td.durationSec = td.framesCount / td.frameRate;

            redrawTrack(trackName);
            toast("Tempo " + atempo + "x applied");
        } catch (Exception ex) {
            ex.printStackTrace();
            alertError("Tempo failed: " + ex.getMessage());
        }
    }

    private void redrawTrack(String trackName) {
        TrackData td = trackDataMap.get(trackName);
        if (td == null) return;

        try {
            File tmp = writeTrackTempWav(trackName, td);
            trackTempFiles.put(trackName, tmp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (var node : tracksPane.getChildren()) {
            if (node instanceof VBox box) {
                Label lbl = (Label) box.getChildren().get(0);
                if (lbl.getText().equals("🎶 " + trackName)) {
                    Canvas canvas = (Canvas) box.getChildren().get(1);
                    drawWaveform(canvas, td, selections.computeIfAbsent(trackName, k -> new Selection()));
                }
            }
        }
    }

    // ======= перетворення, читання/запис =======

    private File ensureWav(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".wav")) return f;

        File tmp = File.createTempFile("import-", ".wav");
        List<String> cmd = List.of(FFMPEG, "-y", "-i", f.getAbsolutePath(), tmp.getAbsolutePath());
        int code = runProcess(cmd);
        if (code != 0) throw new RuntimeException("ffmpeg convert failed: exit " + code);
        return tmp;
    }

    private TrackData readWavToMemory(File wav) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(),
                    16,
                    fmt.getChannels(),
                    fmt.getChannels() * 2,
                    fmt.getSampleRate(),
                    false
            );
            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(target, in);

            byte[] data = pcmStream.readAllBytes();
            TrackData td = new TrackData();
            td.format = target;
            td.pcm = data;
            td.frameSize = target.getFrameSize();
            td.frameRate = target.getFrameRate();
            td.framesCount = data.length / td.frameSize;
            td.durationSec = td.framesCount / td.frameRate;
            return td;
        }
    }

    private File writeTrackTempWav(String trackName, TrackData td) throws Exception {
        File tmp = File.createTempFile("track-" + safe(trackName) + "-", ".wav");
        writePcmToWavFile(td.format, td.pcm, tmp);
        return tmp;
    }

    private File writePcmToTempWav(AudioFormat fmt, byte[] pcm) throws Exception {
        File tmp = File.createTempFile("seg-", ".wav");
        writePcmToWavFile(fmt, pcm, tmp);
        return tmp;
    }

    private void writePcmToWavFile(AudioFormat fmt, byte[] pcm, File out) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
             AudioInputStream ais = new AudioInputStream(bais, fmt, pcm.length / fmt.getFrameSize())) {
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

    // ======= утиліти =======

    private String getActiveTrackName() {
        for (var node : tracksPane.getChildren()) {
            if (node instanceof VBox box) {
                Label lbl = (Label) box.getChildren().get(0);
                return lbl.getText().replace("🎶 ", "");
            }
        }
        return null;
    }

    private int[] selectionToByteRange(Selection sel, TrackData td, double canvasWidth) {
        double L = Math.max(0, Math.min(sel.left(), canvasWidth));
        double R = Math.max(0, Math.min(sel.right(), canvasWidth));
        double fracL = L / canvasWidth;
        double fracR = R / canvasWidth;

        int start = (int) Math.floor(td.pcm.length * fracL);
        int end = (int) Math.ceil(td.pcm.length * fracR);

        start = (start / td.frameSize) * td.frameSize;
        end = (end / td.frameSize) * td.frameSize;
        end = Math.min(end, td.pcm.length);
        if (end <= start) end = Math.min(start + td.frameSize, td.pcm.length);
        return new int[]{start, end};
    }

    private void reverseInPlaceByFrames(byte[] arr, int from, int to, int frameSize) {
        int l = from;
        int r = to - frameSize;
        while (l < r) {
            for (int i = 0; i < frameSize; i++) {
                byte tmp = arr[l + i];
                arr[l + i] = arr[r + i];
                arr[r + i] = tmp;
            }
            l += frameSize;
            r -= frameSize;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private void alertWarn(String m) { new Alert(Alert.AlertType.WARNING, m).showAndWait(); }
    private void alertError(String m) { new Alert(Alert.AlertType.ERROR, m).showAndWait(); }
    private void alertInfo(String m) { new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }
    private void toast(String m) { System.out.println("ℹ " + m); }
    public Parent getRoot() { return root; }
}