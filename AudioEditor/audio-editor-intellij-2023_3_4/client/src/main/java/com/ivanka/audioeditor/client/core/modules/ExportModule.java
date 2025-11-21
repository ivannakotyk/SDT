package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.model.composite.AudioComponent;
import com.ivanka.audioeditor.client.model.composite.AudioProject;
import com.ivanka.audioeditor.client.model.composite.AudioTrack;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;

public class ExportModule extends AbstractColleague {

    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    public ExportModule(EditorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String key() {
        return "Export";
    }

    @Override
    public void receive(EditorEvent e) {
        if (e.type == EditorEventType.EXPORT_REQUEST) {
            onExport();
        }
    }

    private void onExport() {
        AudioProject project = ctx.getAudioProject();
        if (project == null) {
            ctx.alertWarn("No active project to export! Please open a project first.");
            return;
        }

        List<String> exportChoices = new ArrayList<>();
        String mixOption = "Entire Project (Mixed)";
        exportChoices.add(mixOption);
        List<String> trackNames = project.getChildren().stream()
                .map(AudioComponent::getName)
                .toList();
        exportChoices.addAll(trackNames);

        ChoiceDialog<String> exportDlg = new ChoiceDialog<>(exportChoices.get(0), exportChoices);
        exportDlg.setHeaderText("Select what to export");
        exportDlg.setContentText("Export:");
        Optional<String> exportSel = exportDlg.showAndWait();
        if (exportSel.isEmpty()) return;

        String selectedChoice = exportSel.get();

        ChoiceDialog<String> fmtDlg = new ChoiceDialog<>("mp3", List.of("mp3", "ogg", "flac", "wav"));
        fmtDlg.setHeaderText("Select export format");
        fmtDlg.setContentText("Format:");
        Optional<String> fmtSel = fmtDlg.showAndWait();
        if (fmtSel.isEmpty()) return;
        String format = fmtSel.get();

        String defaultName = selectedChoice.equals(mixOption) ? project.getName() + " (Mix)" : selectedChoice;
        TextInputDialog nameDlg = new TextInputDialog(defaultName);
        nameDlg.setHeaderText("Enter file name (without extension)");
        nameDlg.setContentText("Name:");
        Optional<String> nameSel = nameDlg.showAndWait();
        if (nameSel.isEmpty() || nameSel.get().isBlank()) return;

        String exportName = nameSel.get().trim();

        new Thread(() -> {
            File wavToSend = null;
            try {
                if (selectedChoice.equals(mixOption)) {
                    wavToSend = File.createTempFile("proj-mix-", ".wav");
                    ctx.toast("Mixing project... Please wait.");
                    project.exportTo(wavToSend, "wav");
                } else {
                    AudioTrack selectedTrack = (AudioTrack) project.getChildren().stream()
                            .filter(c -> c.getName().equals(selectedChoice))
                            .findFirst()
                            .orElse(null);
                    if (selectedTrack == null) throw new RuntimeException("Track not found");

                    wavToSend = File.createTempFile("track-export-", ".wav");
                    ctx.toast("Exporting track... Please wait.");
                    selectedTrack.exportTo(wavToSend, "wav");
                }

                ctx.toast("Uploading to server for conversion...");
                String response = api.postMultipart("/export", Map.of(
                        "userId", String.valueOf(ctx.getUserId()),
                        "projectId", String.valueOf(ctx.getProject().id),
                        "trackName", exportName,
                        "format", format
                ), wavToSend);

                Map<String, Object> res = mapper.readValue(response, new TypeReference<>() {});
                String serverPath = String.valueOf(res.get("path"));
                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save Exported File");
                    fileChooser.setInitialFileName(exportName + "." + format);
                    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.toUpperCase() + " Audio", "*." + format));

                    File destFile = fileChooser.showSaveDialog(ctx.getStage());

                    if (destFile != null) {
                        downloadExportedFile(serverPath, destFile);
                    } else {
                        ctx.toast("Export cancelled by user.");
                    }
                });

                send(new EditorEvent(EditorEventType.EXPORT_FINISHED).with("ok", true));

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> ctx.alertError("Export failed: " + ex.getMessage()));
            } finally {
                if (wavToSend != null) wavToSend.delete();
            }
        }).start();
    }

    private void downloadExportedFile(String serverPath, File destination) {
        ctx.toast("Downloading file to " + destination.getName() + "...");
        new Thread(() -> {
            try {
                api.downloadFile(serverPath, destination);
                Platform.runLater(() -> ctx.alertInfo("File saved successfully!\nLocation: " + destination.getAbsolutePath()));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> ctx.alertError("Download failed: " + e.getMessage()));
            }
        }).start();
    }
}