package com.ivanka.audioeditor.client.core.observers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import javafx.scene.control.ChoiceDialog;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExportObserver implements Observer {
    private final EditorContext ctx;
    private final ApiClient api = ApiClient.getInstance();
    private final ObjectMapper mapper = new ObjectMapper();

    public ExportObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        if (e.type == EditorEventType.EXPORT_REQUEST) onExport(e);
    }

    private void onExport(EditorEvent e) {
        try {
            if (ctx.getRootItem().getChildren().isEmpty()) {
                ctx.alertWarn("No projects available to export!");
                return;
            }

            // 1) Project select
            List<String> projectNames = ctx.getRootItem().getChildren().stream()
                    .map(javafx.scene.control.TreeItem::getValue)
                    .filter(v -> v.startsWith("🎧"))
                    .toList();

            ChoiceDialog<String> projDlg = new ChoiceDialog<>(projectNames.get(0), projectNames);
            projDlg.setHeaderText("Select project for export");
            projDlg.setContentText("Project:");
            Optional<String> projSel = projDlg.showAndWait();
            if (projSel.isEmpty()) return;

            String projectLabel = projSel.get();
            long selectedProjectId = Long.parseLong(projectLabel.replaceAll("[^0-9]", ""));
            String selectedProjectName = projectLabel.replaceAll("\\s*|\\(id=.*\\)", "").trim();

            // 2) Track select
            List<String> trackNames = ctx.getTrackTempFiles().keySet().stream().toList();
            if (trackNames.isEmpty()) {
                ctx.alertWarn("No audio tracks imported to export!");
                return;
            }

            ChoiceDialog<String> trackDlg = new ChoiceDialog<>(trackNames.get(0), trackNames);
            trackDlg.setHeaderText("Select track to export");
            trackDlg.setContentText("Track:");
            Optional<String> trackSel = trackDlg.showAndWait();
            if (trackSel.isEmpty()) return;

            String selectedTrack = trackSel.get();
            File wavIn = ctx.getTrackTempFiles().get(selectedTrack);
            if (wavIn == null || !wavIn.exists()) {
                ctx.alertError("Selected track has no audio file!");
                return;
            }

            // 3) Format select
            ChoiceDialog<String> fmtDlg = new ChoiceDialog<>("mp3", List.of("mp3", "ogg", "flac", "wav"));
            fmtDlg.setHeaderText("Select export format");
            fmtDlg.setContentText("Format:");
            Optional<String> fmtSel = fmtDlg.showAndWait();
            if (fmtSel.isEmpty()) return;
            String format = fmtSel.get();

            // 4) POST /export (сервер вже має Adapter — ми просто викликаємо бекенд)
            try {
                String response = api.postMultipart("/export", Map.of(
                        "userId", String.valueOf(ctx.getUserId()),
                        "projectId", String.valueOf(selectedProjectId),
                        "trackName", selectedTrack,
                        "format", format
                ), wavIn);

                Map<String, Object> res = mapper.readValue(response, new TypeReference<>() {});
                String path = String.valueOf(res.get("path"));

                ctx.alertInfo("Exported successfully!\nProject: " + selectedProjectName +
                        "\nTrack: " + selectedTrack + "\nFormat: " + format +
                        "\nSaved on server:\n" + path);

                try {
                    File exportedFile = new File(path);
                    if (exportedFile.exists()) {
                        java.awt.Desktop.getDesktop().open(exportedFile);
                    } else {
                        File exportDir = new File("E:\\3 Курс\\ТРПЗ\\AudioEditor\\audio-editor-intellij-2023_3_4\\server\\storage\\exports");
                        if (exportDir.exists()) java.awt.Desktop.getDesktop().open(exportDir);
                    }
                } catch (Exception openEx) {
                    System.err.println("Не вдалося відкрити файл: " + openEx.getMessage());
                }

                // зворотня подія
                com.ivanka.audioeditor.client.core.AudioEditor.getInstance().notifyObservers(
                        new EditorEvent(EditorEventType.EXPORT_FINISHED).with("ok", true).with("path", path)
                );

            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.alertError("Export failed: " + ex.getMessage());
                com.ivanka.audioeditor.client.core.AudioEditor.getInstance().notifyObservers(
                        new EditorEvent(EditorEventType.EXPORT_FINISHED).with("ok", false).with("message", ex.getMessage())
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
