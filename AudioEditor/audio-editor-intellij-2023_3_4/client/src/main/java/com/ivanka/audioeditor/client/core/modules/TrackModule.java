package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.model.composite.AudioComponent;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioSegment;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.common.dto.TrackDTO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class TrackModule extends AbstractColleague {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Canvas> canvases = new HashMap<>();
    private final Map<String, Slider> cursors = new HashMap<>();
    private static final double CANVAS_W = 900;
    private static final double CANVAS_H = 160;

    public TrackModule(EditorContext ctx) { this.ctx = ctx; }

    @Override public String key() { return "Track"; }
    public Map<String, Slider> getCursors() { return cursors; }

    @Override
    public void receive(EditorEvent e) {
        try {
            switch (e.type) {
                case TRACK_ADD_REQUEST -> onAddTrack(e);
                case TRACKS_REFRESH_REQUEST -> onRefreshTracks(e);
                case AUDIO_IMPORTED -> onAudioImported(e);
                case PLAYBACK_PROGRESS -> onPlaybackProgress(e);
                case PLAYBACK_FINISHED -> onPlaybackFinished(e);
                default -> {}
            }
        } catch (Exception ex) {
            ctx.alertError("Track error: " + ex.getMessage());
        }
    }

    private void onAddTrack(EditorEvent e) throws Exception {
        long pid = e.get("projectId");
        String name = e.get("trackName").toString().trim();

        AudioProject project = ctx.getAudioProject();
        if (project != null && project.getChildren().stream().noneMatch(c -> c.getName().equals(name))) {
            project.add(new AudioTrack(name));
        }

        String url = "/projects/" + pid + "/tracks?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        api.postJson(url, Map.of());
        send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", ctx.getCurrentProjectNode()));
    }

    private void onRefreshTracks(EditorEvent e) throws Exception {
        TreeItem<String> node = e.get("projectNode");
        if (node == null) return;
        long pid = ctx.getProject().id;

        String resp = api.get("/projects/" + pid + "/tracks");
        List<TrackDTO> dtos = mapper.readValue(resp, new TypeReference<>() {});
        List<ProjectTrack> list = dtos.stream()
                .map(dto -> new ProjectTrack(dto.id(), dto.name(), dto.order()))
                .collect(Collectors.toList());

        ctx.getTrackCache().put(pid, list);

        Platform.runLater(() -> {
            AudioProject audioProj = ctx.getAudioProject();

            if (audioProj != null) {
                Set<String> serverTrackNames = list.stream().map(ProjectTrack::getTrackName).collect(Collectors.toSet());
                List<AudioComponent> toRemove = new ArrayList<>();
                for (AudioComponent c : audioProj.getChildren()) {
                    if (!serverTrackNames.contains(c.getName())) {
                        toRemove.add(c);
                    }
                }

                toRemove.forEach(audioProj::remove);
                for (ProjectTrack pt : list) {
                    boolean exists = audioProj.getChildren().stream().anyMatch(c -> c.getName().equals(pt.getTrackName()));
                    if (!exists) {
                        audioProj.add(new AudioTrack(pt.getTrackName()));
                    }
                }
            }

            ctx.getTracksPane().getChildren().clear();
            canvases.clear();
            cursors.clear();
            for (ProjectTrack t : list) {
                ctx.getTracksPane().getChildren().add(buildTrackBox(t.getTrackName()));
            }

            node.getChildren().clear();
            for (ProjectTrack t : list) node.getChildren().add(new TreeItem<>(t.getTrackName()));
            node.setExpanded(true);
        });
    }

    private void onAudioImported(EditorEvent e) {
        String trackName = e.get("trackName");
        Platform.runLater(() -> {
            ctx.redrawTrack(trackName);
            drawCursorLine(trackName);
        });
    }

    private VBox buildTrackBox(String trackName) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(6));
        Label title = new Label(trackName);
        title.setTextFill(Color.WHITE);
        title.setOnMouseClicked(ev -> ctx.setActiveTrackName(trackName));

        Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);
        canvases.put(trackName, canvas);

        setupMouseSelectionAndCursor(trackName, canvas);
        ctx.drawWaveform(canvas, trackName);
        drawCursorLine(trackName);

        HBox controls = buildControlBar(trackName);
        box.getChildren().addAll(title, canvas, controls);
        return box;
    }

    private void setupMouseSelectionAndCursor(String trackName, Canvas canvas) {
        canvas.setOnMousePressed(ev -> {
            ctx.setActiveTrackName(trackName);
            var sel = ctx.getSelections().computeIfAbsent(trackName, k -> new EditorContext.Selection());
            double x = clamp(ev.getX(), 0, canvas.getWidth());
            sel.xStart = x;
            sel.xEnd = x;
            ctx.redrawTrack(trackName);
            drawCursorLine(trackName);
        });
        canvas.setOnMouseDragged(ev -> {
            var sel = ctx.getSelections().get(trackName);
            if (sel != null) {
                sel.xEnd = clamp(ev.getX(), 0, canvas.getWidth());
                ctx.redrawTrack(trackName);
                drawCursorLine(trackName);
            }
        });
        canvas.setOnMouseReleased(ev -> {
            var sel = ctx.getSelections().get(trackName);
            if (sel != null) {
                sel.xEnd = clamp(ev.getX(), 0, canvas.getWidth());
                double w = Math.abs(sel.xEnd - sel.xStart);
                if (w < 3.0) {
                    sel.clear();
                    Slider sl = cursors.get(trackName);
                    double frac = clamp(ev.getX() / canvas.getWidth(), 0, 1);
                    if (sl != null) sl.setValue(frac);
                    ctx.setActiveTrackCursor(frac);
                }
                ctx.redrawTrack(trackName);
                drawCursorLine(trackName);
            }
        });

        canvas.setOnMouseClicked(ev -> {
            ctx.setActiveTrackName(trackName);
            if (ev.isControlDown()) {
                double frac = clamp(ev.getX() / canvas.getWidth(), 0, 1);
                Slider sl = cursors.get(trackName);
                if (sl != null) sl.setValue(frac);
                ctx.setActiveTrackCursor(frac);

                AudioSegment seg = getMainSegment(trackName);
                if (seg != null && seg.getDurationSec() > 0) {
                    double sec = frac * seg.getDurationSec();
                    AudioEditor.getInstance().notifyObservers(
                            new EditorEvent(EditorEventType.PLAYBACK_START)
                                    .with("trackName", trackName)
                                    .with("startAtSec", sec)
                    );
                }
                drawCursorLine(trackName);
            }
        });
    }


    private HBox buildControlBar(String trackName) {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(4, 0, 0, 0));

        Button btnDel = new Button("X");
        btnDel.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
        btnDel.setTooltip(new Tooltip("Delete Track"));

        btnDel.setOnAction(ev -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete track '" + trackName + "'?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.YES) {
                    deleteTrack(trackName);
                }
            });
        });

        Button btnPlay = new Button("Play");
        Button btnStop = new Button("Stop");
        Button btnCopy = new Button("Copy");
        Button btnCut = new Button("Cut");
        Button btnPaste = new Button("Paste");
        Button btnRev = new Button("Rev");

        ChoiceBox<String> speed = new ChoiceBox<>();
        speed.getItems().addAll("0.5x","1x","1.5x","2x");
        speed.setValue("1x");

        Slider cursor = new Slider(0, 1, 0);
        cursor.setPrefWidth(200);
        cursors.put(trackName, cursor);

        Label timeLabel = new Label("00:00.000");
        timeLabel.setTextFill(Color.web("#cbd5e1"));

        btnPlay.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);
            var editor = AudioEditor.getInstance();
            AudioSegment seg = getMainSegment(trackName);
            if (seg == null || seg.getDurationSec() <= 0) { ctx.alertWarn("Import audio to this track first."); return; }

            double frac = cursor.getValue();
            ctx.setActiveTrackCursor(frac);
            double sec = frac * seg.getDurationSec();

            editor.notifyObservers(new EditorEvent(EditorEventType.PLAYBACK_START)
                    .with("trackName", trackName)
                    .with("startAtSec", sec));
        });

        btnStop.setOnAction(ev ->
                AudioEditor.getInstance().notifyObservers(
                        new EditorEvent(EditorEventType.PLAYBACK_STOP).with("trackName", trackName))
        );

        cursor.setOnMouseReleased(ev -> {
            ctx.setActiveTrackName(trackName);
            AudioSegment seg = getMainSegment(trackName);
            if (seg == null || seg.getDurationSec() <= 0) return;

            double frac = cursor.getValue();
            ctx.setActiveTrackCursor(frac);
            double sec = frac * seg.getDurationSec();

            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.PLAYBACK_START)
                            .with("trackName", trackName)
                            .with("startAtSec", sec)
            );
        });

        btnCopy.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.CLIPBOARD_COPY).with("trackName", trackName));
        });
        btnCut.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.CLIPBOARD_CUT).with("trackName", trackName));
        });
        btnRev.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.WAVEFORM_REDRAW)
                            .with("trackName", trackName)
                            .with("fx", "reverse"));
        });
        speed.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);
            String v = speed.getValue();
            double k = Double.parseDouble(v.replace("x",""));
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.WAVEFORM_REDRAW)
                            .with("trackName", trackName)
                            .with("fx", "atempo:" + k));
        });

        btnPaste.setOnAction(ev -> {
            ctx.setActiveTrackName(trackName);

            double frac = Optional.ofNullable(cursors.get(trackName))
                    .map(Slider::getValue)
                    .orElse(0.0);
            ctx.setActiveTrackCursor(frac);

            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.CLIPBOARD_PASTE)
                            .with("trackName", trackName)
                            .with("cursorFrac", ctx.getActiveTrackCursor()));
        });

        cursor.valueProperty().addListener((obs, o, n) -> {
            if (trackName.equals(ctx.getActiveTrackName())) {
                ctx.setActiveTrackCursor(n.doubleValue());
            }
            AudioSegment seg = getMainSegment(trackName);
            double posSec = (seg == null ? 0.0 : n.doubleValue() * seg.getDurationSec());
            timeLabel.setText(formatTime(posSec));
            drawCursorLine(trackName);
        });

        bar.getChildren().addAll(
                btnDel, new Separator(),
                btnPlay, btnStop, new Separator(),
                btnCopy, btnCut, btnPaste, new Separator(),
                btnRev, new Label("Speed:"), speed, new Separator(),
                new Label("Cursor:"), cursor, timeLabel
        );
        return bar;
    }

    private void deleteTrack(String trackName) {
        try {
            long projectId = ctx.getProject().id;
            List<ProjectTrack> tracks = ctx.getTrackCache().get(projectId);
            Long trackId = null;
            if (tracks != null) {
                for (ProjectTrack t : tracks) {
                    if (t.getTrackName().equals(trackName)) {
                        trackId = t.getId();
                        break;
                    }
                }
            }

            if (trackId != null) {
                api.delete("/tracks/" + trackId);
            }

            AudioProject project = ctx.getAudioProject();
            if (project != null) {
                var child = project.getChildren().stream()
                        .filter(c -> c.getName().equals(trackName))
                        .findFirst().orElse(null);
                if (child != null) {
                    project.remove(child);
                }
            }

            send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", ctx.getCurrentProjectNode()));
            ctx.toast("Track deleted.");

        } catch (Exception e) {
            e.printStackTrace();
            ctx.alertError("Error deleting track: " + e.getMessage());
        }
    }

    private void onPlaybackProgress(EditorEvent e) {
        String trackName = e.get("trackName");
        Double fracObj = e.get("fraction");
        if (trackName == null || fracObj == null) return;

        if (!trackName.equals(ctx.getActiveTrackName())) return;

        Slider sl = cursors.get(trackName);
        if (sl == null) return;

        double frac = Math.max(0, Math.min(1, fracObj));

        Platform.runLater(() -> {
            sl.setValue(frac);
            ctx.setActiveTrackCursor(frac);
        });
    }

    private void onPlaybackFinished(EditorEvent e) {
        String trackName = e.get("trackName");
        if (trackName == null) return;
        if (!trackName.equals(ctx.getActiveTrackName())) return;

        Slider sl = cursors.get(trackName);
        if (sl == null) return;

        Platform.runLater(() -> {
            sl.setValue(1.0);
            ctx.setActiveTrackCursor(1.0);
        });
    }

    private String formatTime(double sec) {
        long ms = (long) Math.round(Math.max(0, sec) * 1000.0);
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        long msec = ms % 1000;
        return String.format(Locale.US, "%02d:%02d.%03d", mins, secs, msec);
    }

    private void drawCursorLine(String trackName) {
        Canvas cv = canvases.get(trackName);
        if (cv == null) return;

        AudioSegment seg = getMainSegment(trackName);

        ctx.drawWaveform(cv, trackName);

        if (seg == null || seg.getDurationSec() <= 0) {
            GraphicsContext g = cv.getGraphicsContext2D();
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.0);
            g.strokeLine(0, 0, 0, cv.getHeight());
            return;
        }

        Slider sl = cursors.get(trackName);
        double x = sl == null ? 0 : sl.getValue() * cv.getWidth();

        GraphicsContext g = cv.getGraphicsContext2D();
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.0);
        g.strokeLine(x, 0, x, cv.getHeight());
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private AudioTrack getTrack(String name) {
        AudioProject project = ctx.getAudioProject();
        if (project == null) return null;
        return (AudioTrack) project.getChildren().stream().filter(c -> c instanceof AudioTrack && c.getName().equals(name)).findFirst().orElse(null);
    }

    private AudioSegment getMainSegment(String trackName) {
        AudioTrack track = getTrack(trackName);
        if (track == null || track.getChildren().isEmpty()) return null;
        return (AudioSegment) track.getChildren().get(0);
    }
}