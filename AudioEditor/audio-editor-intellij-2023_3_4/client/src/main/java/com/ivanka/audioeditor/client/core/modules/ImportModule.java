package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.ivanka.audioeditor.client.ui.EditorContext.TrackData;

public class ImportModule extends AbstractColleague {

    private final EditorContext ctx;

    public ImportModule(EditorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String key() {
        return "Import";
    }

    @Override
    public void receive(EditorEvent e) {
        if (e.type != EditorEventType.IMPORT_REQUEST) return;

        try {
            if (ctx.getProject() == null || ctx.getProject().id <= 0) {
                ctx.alertWarn("Спочатку оберіть або створіть проєкт.");
                return;
            }

            Stage stage = ctx.getStage();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio File");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.ogg", "*.flac")
            );

            File f = fc.showOpenDialog(stage);
            if (f == null) return;

            List<String> trackNames = getTrackNamesFromPane();
            if (trackNames.isEmpty()) {
                ctx.alertWarn("Немає треків для імпорту. Спочатку додайте трек.");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(trackNames.get(0), trackNames);
            dialog.setHeaderText("Оберіть трек для імпорту аудіо");
            dialog.setContentText("Трек:");
            dialog.showAndWait().ifPresent(trackName -> {
                try {
                    File wav = ctx.ensureWav(f);
                    TrackData td = ctx.readWavToMemory(wav);

                    long projectId = ctx.getProject().id;
                    String key = projectId + ":" + trackName.trim();

                    ctx.getTrackTempFiles().put(key, wav);
                    ctx.getTrackDataMap().put(key, td);

                    AudioEditor.getInstance().notifyObservers(
                            new EditorEvent(EditorEventType.AUDIO_IMPORTED)
                                    .with("trackName", trackName)
                                    .with("trackData", td)
                                    .with("projectId", projectId)
                    );

                } catch (Exception ex) {
                    ex.printStackTrace();
                    ctx.alertError("Помилка імпорту: " + ex.getMessage());
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Import failed: " + ex.getMessage());
        }
    }

    private List<String> getTrackNamesFromPane() {
        var names = new ArrayList<String>();
        ctx.getTracksPane().getChildren().forEach(node -> {
            if (node instanceof VBox box && !box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl) {
                names.add(lbl.getText().trim());
            }
        });
        return names;
    }
}
