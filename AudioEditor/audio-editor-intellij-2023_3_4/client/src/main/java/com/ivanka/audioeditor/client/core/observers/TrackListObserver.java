package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.model.ProjectTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static com.ivanka.audioeditor.client.ui.EditorContext.Selection;

public class TrackListObserver implements Observer {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();

    public TrackListObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case TRACK_ADD_REQUEST -> onAddRequest(e);
            case TRACK_ADDED -> onAdded(e);
            default -> {}
        }
    }

    private void onAddRequest(EditorEvent e) {
        if (ctx.getProject().id == 0 || ctx.getCurrentProjectNode() == null) {
            ctx.alertWarn("Select or create a project first!");
            return;
        }
        String name = e.get("trackName");
        try {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            api.postForm("/projects/" + ctx.getProject().id + "/tracks", "name=" + encodedName);
            ctx.getCurrentProjectNode().getChildren().add(new TreeItem<>(" " + name));
            ctx.getTrackCache().computeIfAbsent(ctx.getProject().id, k -> new ArrayList<>())
                    .add(new ProjectTrack(0, name, 0));

            onAdded(new EditorEvent(EditorEventType.TRACK_ADDED)
                    .with("projectId", ctx.getProject().id)
                    .with("trackName", name));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void onAdded(EditorEvent e) {
        String name = e.get("trackName");

        Label trackLabel = new Label(" " + name);
        trackLabel.setTextFill(Color.web("#7dd3fc"));
        trackLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Canvas canvas = new Canvas(900, 140);

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

        ctx.getTracksPane().getChildren().add(box);

        var td = ctx.getTrackDataMap().get(name);
        if (td == null) ctx.drawEmptyBackground(canvas, "No audio");
        else ctx.drawWaveform(canvas, td, ctx.getSelections().computeIfAbsent(name, k -> new Selection()));
        var sel = ctx.getSelections().computeIfAbsent(name, k -> new Selection());
        canvas.setOnMousePressed(ev -> {
            sel.xStart = clamp(ev.getX(), 0, canvas.getWidth());
            sel.xEnd = sel.xStart;
            ctx.drawWaveform(canvas, ctx.getTrackDataMap().get(name), sel);
        });
        canvas.setOnMouseDragged(ev -> {
            sel.xEnd = clamp(ev.getX(), 0, canvas.getWidth());
            ctx.drawWaveform(canvas, ctx.getTrackDataMap().get(name), sel);
        });
        canvas.setOnMouseReleased(ev -> {
            sel.xEnd = clamp(ev.getX(), 0, canvas.getWidth());
            ctx.drawWaveform(canvas, ctx.getTrackDataMap().get(name), sel);
        });

        // Кнопки -> події
        play.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.PLAYBACK_START).with("trackName", name)));
        stop.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.PLAYBACK_STOP)));
        copy.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_COPY).with("trackName", name)));
        paste.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_PASTE).with("trackName", name)));
        cut.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.CLIPBOARD_CUT).with("trackName", name)));
        reverse.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.WAVEFORM_REDRAW).with("trackName", name).with("fx", "reverse")));
        speedUp.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.WAVEFORM_REDRAW).with("trackName", name).with("fx", "atempo:1.25")));
        slowDown.setOnAction(ae -> com.ivanka.audioeditor.client.core.AudioEditor.getInstance()
                .notifyObservers(new EditorEvent(EditorEventType.WAVEFORM_REDRAW).with("trackName", name).with("fx", "atempo:0.75")));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
