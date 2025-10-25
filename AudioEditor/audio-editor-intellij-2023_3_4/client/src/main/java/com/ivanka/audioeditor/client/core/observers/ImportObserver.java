package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class ImportObserver implements Observer {
    private final EditorContext ctx;

    public ImportObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        if (e.type == EditorEventType.IMPORT_REQUEST) onImport(e);
    }

    private void onImport(EditorEvent e) {
        if (ctx.getProject().id == 0) return;
        try {
            Stage stage = ctx.getStage();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio File");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.ogg", "*.flac")
            );
            File f = fc.showOpenDialog(stage);
            if (f == null) return;

            List<String> trackNames = ctx.getTracksPane().getChildren().stream()
                    .filter(node -> node instanceof javafx.scene.layout.VBox)
                    .map(node -> ((javafx.scene.control.Label)((javafx.scene.layout.VBox) node).getChildren().get(0)).getText().replace("🎶 ", ""))
                    .toList();

            if (trackNames.isEmpty()) {
                ctx.alertWarn("No tracks to import into! Create a track first.");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(trackNames.get(0), trackNames);
            dialog.setHeaderText("Select track to import audio");
            dialog.setContentText("Track:");
            dialog.showAndWait().ifPresent(trackName -> {
                try {
                    File wav = ctx.ensureWav(f);
                    var td = ctx.readWavToMemory(wav);
                    AudioEditor.getInstance().notifyObservers(
                            new EditorEvent(EditorEventType.AUDIO_IMPORTED)
                                    .with("trackName", trackName)
                                    .with("trackData", td)
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ctx.alertError("Import failed: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
