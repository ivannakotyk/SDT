package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.AudioEditor;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.AudioComponent;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioSegment;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.model.composite.PcmUtils;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.client.ui.EditorView;
import com.ivanka.audioeditor.common.dto.SegmentDTO;
import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImportModule extends AbstractColleague {

    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportModule(EditorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String key() { return "Import"; }

    @Override
    public void receive(EditorEvent e) {
        if (e.type != EditorEventType.IMPORT_REQUEST) return;
        onImportRequest();
    }

    private void onImportRequest() {
        try {
            if (ctx.getProject() == null || ctx.getProject().id <= 0) {
                ctx.alertWarn("Спочатку оберіть або створіть проєкт.");
                return;
            }

            Stage stage = ctx.getStage();
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio", "*.wav", "*.mp3", "*.flac"));
            File f = fc.showOpenDialog(stage);
            if (f == null) return;

            List<String> trackNames = getTrackNamesFromPane();
            if (trackNames.isEmpty()) {
                ctx.alertWarn("Додайте трек перед імпортом.");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(trackNames.get(0), trackNames);
            dialog.setHeaderText("Оберіть трек");
            dialog.setContentText("Трек:");
            String trackName = dialog.showAndWait().orElse(null);
            if (trackName == null) return;

            ctx.toast("Завантаження...");

            new Thread(() -> {
                try {
                    long trackId = findTrackIdByName(trackName);
                    if (trackId == -1) throw new RuntimeException("Track ID error");

                    File wavFile = ((EditorView) ctx).ensureWav(f);

                    String jsonResponse = api.postMultipart("/segments/import/" + trackId, Map.of(), wavFile);

                    SegmentDTO dto = mapper.readValue(jsonResponse, SegmentDTO.class);

                    AudioFormat[] fmt = new AudioFormat[1];
                    float[][] samples = PcmUtils.readWavStereo(wavFile, fmt);

                    Platform.runLater(() -> {
                        try {
                            AudioProject project = ctx.getAudioProject();
                            AudioTrack track = (AudioTrack) project.getChildren().stream()
                                    .filter(c -> c.getName().equals(trackName))
                                    .findFirst().orElse(null);

                            if (track != null) {
                                List<AudioComponent> existing = new ArrayList<>(track.getChildren());
                                for (AudioComponent c : existing) track.remove(c);

                                AudioSegment segment = new AudioSegment(dto.name(), samples, fmt[0]);
                                segment.setId(dto.id()); // ID з сервера
                                track.add(segment);

                                ctx.toast("Імпорт успішний!");
                                AudioEditor.getInstance().notifyObservers(
                                        new EditorEvent(EditorEventType.AUDIO_IMPORTED).with("trackName", trackName));
                            }
                        } catch (Exception uiEx) {
                            uiEx.printStackTrace();
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> ctx.alertError("Error: " + ex.getMessage()));
                }
            }).start();

        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private long findTrackIdByName(String name) {
        var cache = ctx.getTrackCache().get(ctx.getProject().id);
        if (cache == null) return -1;
        return cache.stream().filter(t -> t.getTrackName().equals(name))
                .findFirst().map(com.ivanka.audioeditor.client.model.ProjectTrack::getId).orElse(-1L);
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