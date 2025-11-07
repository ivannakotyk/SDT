package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TrackModule extends AbstractColleague {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Canvas> canvases = new HashMap<>();
    private final Map<String, Slider> cursors  = new HashMap<>();
    private static final double CANVAS_W = 900;
    private static final double CANVAS_H = 160;

    public TrackModule(EditorContext ctx) { this.ctx = ctx; }

    @Override public String key() { return "Track"; }

    @Override
    public void receive(EditorEvent e) {
        try {
            switch (e.type) {
                case TRACK_ADD_REQUEST      -> onAddTrack(e);
                case TRACKS_REFRESH_REQUEST -> onRefreshTracks(e);
                case AUDIO_IMPORTED         -> onAudioImported(e);

                case PLAYBACK_PROGRESS      -> onPlaybackProgress(e);
                case PLAYBACK_FINISHED      -> onPlaybackFinished(e);


                default -> {}
            }
        } catch (Exception ex) {
            ctx.alertError("Track error: " + ex.getMessage());
        }
    }

    private void onAddTrack(EditorEvent e) throws Exception {
        long pid   = e.get("projectId");
        String name = e.get("trackName");
        String url = "/projects/" + pid + "/tracks?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        api.postJson(url, Map.of());
        send(new EditorEvent(EditorEventType.TRACKS_REFRESH_REQUEST).with("projectNode", ctx.getCurrentProjectNode()));
    }

    private void onRefreshTracks(EditorEvent e) throws Exception {
        TreeItem<String> node = e.get("projectNode");
        if (node == null) return;
        long pid = ctx.getProject().id;

        String resp = api.get("/projects/" + pid + "/tracks");
        List<ProjectTrack> list = mapper.readValue(resp, new TypeReference<>() {});
        ctx.getTrackCache().put(pid, list);

        Platform.runLater(() -> {
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
            Canvas cv = canvases.get(trackName);
            if (cv != null) {
                ctx.redrawTrack(trackName);
                drawCursorLine(trackName);
            }
        });
    }
    private VBox buildTrackBox(String trackName) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(6));

        Label title = new Label(trackName);
        title.setTextFill(Color.WHITE);

        Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);
        canvases.put(trackName, canvas);
        setupMouseSelectionAndCursor(trackName, canvas);

        var td = ctx.getTrackDataMap().get(trackName);
        if (td == null) ctx.drawEmptyBackground(canvas, "No audio data. Import a file to this track.");
        else { ctx.redrawTrack(trackName); drawCursorLine(trackName); }

        HBox controls = buildControlBar(trackName);
        box.getChildren().addAll(title, canvas, controls);
        return box;
    }

    private void setupMouseSelectionAndCursor(String trackName, Canvas canvas) {
        canvas.setOnMousePressed(ev -> {
            var sel = ctx.getSelections().computeIfAbsent(trackName, k -> new EditorContext.Selection());
            double x = clamp(ev.getX(), 0, canvas.getWidth());
            sel.xStart = x;
            sel.xEnd   = x;
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
                    if (sl != null) sl.setValue(ev.getX() / canvas.getWidth());
                }
                ctx.redrawTrack(trackName);
                drawCursorLine(trackName);
            }
        });

        canvas.setOnMouseClicked(ev -> {
            if (ev.isControlDown()) {
                double frac = clamp(ev.getX() / canvas.getWidth(), 0, 1);
                Slider sl = cursors.get(trackName);
                if (sl != null) sl.setValue(frac);
                var td = ctx.getTrackDataMap().get(trackName);
                if (td != null && td.durationSec > 0) {
                    double sec = frac * td.durationSec;
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

        Button btnPlay   = new Button("Play");
        Button btnStop   = new Button("Stop");
        Button btnCopy   = new Button("Copy");
        Button btnCut    = new Button("Cut");
        Button btnPaste  = new Button("Paste");
        Button btnRev    = new Button("Reverse");

        ChoiceBox<String> speed = new ChoiceBox<>();
        speed.getItems().addAll("0.5x","1x","1.5x","2x");
        speed.setValue("1x");

        Slider cursor = new Slider(0, 1, 0);
        cursor.setPrefWidth(320);
        cursors.put(trackName, cursor);

        Label timeLabel = new Label("00:00.000");
        timeLabel.setTextFill(Color.web("#cbd5e1"));

        btnPlay.setOnAction(ev -> {
            var editor = AudioEditor.getInstance();
            var td = ctx.getTrackDataMap().get(trackName);
            if (td == null || td.durationSec <= 0) { ctx.alertWarn("Import audio to this track first."); return; }
            double sec = cursor.getValue() * td.durationSec;
            editor.notifyObservers(new EditorEvent(EditorEventType.PLAYBACK_START)
                    .with("trackName", trackName)
                    .with("startAtSec", sec));
        });

        btnStop.setOnAction(ev ->
                AudioEditor.getInstance().notifyObservers(
                        new EditorEvent(EditorEventType.PLAYBACK_STOP).with("trackName", trackName))
        );

        cursor.setOnMouseReleased(ev -> {
            var td = ctx.getTrackDataMap().get(trackName);
            if (td == null || td.durationSec <= 0) return;
            double sec = cursor.getValue() * td.durationSec;
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.PLAYBACK_START)
                            .with("trackName", trackName)
                            .with("startAtSec", sec)
            );
        });

        btnCopy.setOnAction(ev -> AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.CLIPBOARD_COPY).with("trackName", trackName)));
        btnCut.setOnAction(ev -> AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.CLIPBOARD_CUT).with("trackName", trackName)));
        btnPaste.setOnAction(ev -> AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.CLIPBOARD_PASTE)
                        .with("trackName", trackName)
                        .with("cursorFrac",
                                Optional.ofNullable(cursors.get(trackName)).map(Slider::getValue).orElse(0.0))));
        btnRev.setOnAction(ev -> AudioEditor.getInstance().notifyObservers(
                new EditorEvent(EditorEventType.WAVEFORM_REDRAW)
                        .with("trackName", trackName)
                        .with("fx", "reverse")));

        speed.setOnAction(ev -> {
            String v = speed.getValue();
            double k = Double.parseDouble(v.replace("x",""));
            AudioEditor.getInstance().notifyObservers(
                    new EditorEvent(EditorEventType.WAVEFORM_REDRAW)
                            .with("trackName", trackName)
                            .with("fx", "atempo:" + k));
        });

        cursor.valueProperty().addListener((obs, o, n) -> {
            var td = ctx.getTrackDataMap().get(trackName);
            double posSec = (td == null ? 0.0 : n.doubleValue() * td.durationSec);
            timeLabel.setText(formatTime(posSec));
            drawCursorLine(trackName);
        });

        bar.getChildren().addAll(
                btnPlay, btnStop, new Separator(),
                btnCopy, btnCut, btnPaste, new Separator(),
                btnRev, new Label("Speed:"), speed, new Separator(),
                new Label("Cursor:"), cursor, timeLabel
        );
        return bar;
    }

    private void onPlaybackProgress(EditorEvent e) {
        String trackName = e.get("trackName");
        Double fracObj = e.get("fraction");
        if (trackName == null || fracObj == null) return;

        Slider sl = cursors.get(trackName);
        if (sl == null) return;

        double frac = Math.max(0, Math.min(1, fracObj));

        Platform.runLater(() -> {
            sl.setValue(frac);
        });
    }

    private void onPlaybackFinished(EditorEvent e) {
        String trackName = e.get("trackName");
        if (trackName == null) return;

        Slider sl = cursors.get(trackName);
        if (sl == null) return;

        Platform.runLater(() -> {
            sl.setValue(1.0);
        });
    }

    private String formatTime(double sec) {
        long ms   = (long) Math.round(Math.max(0, sec) * 1000.0);
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        long msec = ms % 1000;
        return String.format(Locale.US, "%02d:%02d.%03d", mins, secs, msec);
    }

    private void drawCursorLine(String trackName) {
        Canvas cv = canvases.get(trackName);
        if (cv == null) return;
        var td = ctx.getTrackDataMap().get(trackName);
        if (td == null || td.durationSec <= 0) {

            ctx.drawWaveform(cv, td, ctx.getSelections().computeIfAbsent(trackName, k -> new EditorContext.Selection()));
            GraphicsContext g = cv.getGraphicsContext2D();
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.0);
            g.strokeLine(0, 0, 0, cv.getHeight());
            return;
        }

        Slider sl = cursors.get(trackName);
        double x = sl == null ? 0 : sl.getValue() * cv.getWidth();

        ctx.drawWaveform(cv, td, ctx.getSelections().computeIfAbsent(trackName, k -> new EditorContext.Selection()));
        GraphicsContext g = cv.getGraphicsContext2D();
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.0);
        g.strokeLine(x, 0, x, cv.getHeight());
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}