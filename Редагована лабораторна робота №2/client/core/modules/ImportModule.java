package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioSegment;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.model.composite.PcmUtils;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.client.ui.EditorView;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

            final String trackName = dialog.showAndWait().orElse(null);
            if (trackName == null) return;
            ctx.toast("Importing file: " + f.getName() + "...");
            new Thread(() -> {
                try {
                    File wav = ((EditorView) ctx).ensureWav(f);

                    AudioFormat[] fmt = new AudioFormat[1];
                    float[][] stereoSamples = PcmUtils.readWavStereo(wav, fmt);
                    AudioFormat format = fmt[0];

                    AudioProject project = ctx.getAudioProject();
                    AudioTrack track = (AudioTrack) project.getChildren().stream()
                            .filter(c -> c instanceof AudioTrack && c.getName().equals(trackName))
                            .findFirst()
                            .orElse(null);

                    if (track == null) {
                        track = new AudioTrack(trackName);
                        project.add(track);
                    }

                    AudioSegment segment = new AudioSegment(f.getName(), stereoSamples, format);
                    track.add(segment);

                    ctx.toast("Import complete: " + f.getName());
                    AudioEditor.getInstance().notifyObservers(
                            new EditorEvent(EditorEventType.AUDIO_IMPORTED)
                                    .with("trackName", trackName)
                                    .with("projectId", ctx.getProject().id)
                    );

                } catch (Exception ex) {
                    ex.printStackTrace();
                    ctx.alertError("Помилка імпорту: " + ex.getMessage());
                }
            }).start();

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