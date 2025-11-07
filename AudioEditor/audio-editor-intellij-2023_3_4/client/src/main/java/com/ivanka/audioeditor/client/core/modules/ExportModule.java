package com.ivanka.audioeditor.client.core.modules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.net.ApiClient;
import com.ivanka.audioeditor.client.ui.EditorContext;
import com.ivanka.audioeditor.client.ui.tree.ProjectTreeItem;
import javafx.scene.control.ChoiceDialog;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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
            onExport(e);
        }
    }

    private void onExport(EditorEvent e) {
        try {
            List<ProjectTreeItem> projects = ctx.getRootItem().getChildren().stream()
                    .filter(item -> item instanceof ProjectTreeItem)
                    .map(item -> (ProjectTreeItem) item)
                    .collect(Collectors.toList());

            if (projects.isEmpty()) {
                ctx.alertWarn("No projects available to export!");
                return;
            }

            List<String> projectNames = projects.stream()
                    .map(ProjectTreeItem::getValue)
                    .toList();

            ChoiceDialog<String> projDlg = new ChoiceDialog<>(projectNames.get(0), projectNames);
            projDlg.setHeaderText("Select project for export");
            projDlg.setContentText("Project:");
            Optional<String> projSel = projDlg.showAndWait();
            if (projSel.isEmpty()) return;

            String selectedProjectName = projSel.get();
            ProjectTreeItem selectedItem = projects.stream()
                    .filter(p -> p.getValue().equals(selectedProjectName))
                    .findFirst()
                    .orElse(null);
            if (selectedItem == null) {
                ctx.alertError("Project not found in tree!");
                return;
            }
            long selectedProjectId = selectedItem.getProjectId();

            Map<String, File> trackFiles = ctx.getTrackTempFiles();
            List<String> allTracks = trackFiles.entrySet().stream()
                    .filter(e2 -> e2.getKey().startsWith(selectedProjectId + ":"))
                    .map(e2 -> e2.getKey().substring((selectedProjectId + ":").length()))
                    .toList();

            if (allTracks.isEmpty()) {
                ctx.alertWarn("No audio tracks imported in this project!");
                return;
            }

            ChoiceDialog<String> trackDlg = new ChoiceDialog<>(allTracks.get(0), allTracks);
            trackDlg.setHeaderText("Select track to export");
            trackDlg.setContentText("Track:");
            Optional<String> trackSel = trackDlg.showAndWait();
            if (trackSel.isEmpty()) return;

            String selectedTrack = trackSel.get();
            String key = selectedProjectId + ":" + selectedTrack;
            File wavIn = trackFiles.get(key);

            if (wavIn == null || !wavIn.exists()) {
                ctx.alertError("Selected track has no audio file!");
                return;
            }

            ChoiceDialog<String> fmtDlg = new ChoiceDialog<>("mp3", List.of("mp3", "ogg", "flac", "wav"));
            fmtDlg.setHeaderText("Select export format");
            fmtDlg.setContentText("Format:");
            Optional<String> fmtSel = fmtDlg.showAndWait();
            if (fmtSel.isEmpty()) return;
            String format = fmtSel.get();

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

                openExportedFile(path);

                // Callback
                send(new EditorEvent(EditorEventType.EXPORT_FINISHED)
                        .with("ok", true)
                        .with("path", path));

            } catch (Exception ex) {
                ex.printStackTrace();
                ctx.alertError("Export failed: " + ex.getMessage());

                send(new EditorEvent(EditorEventType.EXPORT_FINISHED)
                        .with("ok", false)
                        .with("message", ex.getMessage()));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.alertError("Unexpected export error: " + ex.getMessage());
        }
    }

    private void openExportedFile(String path) {
        try {
            File exportedFile = new File(path);
            if (exportedFile.exists()) {
                java.awt.Desktop.getDesktop().open(exportedFile);
            } else {
                File exportDir = new File("server/storage/exports");
                if (exportDir.exists()) java.awt.Desktop.getDesktop().open(exportDir);
            }
        } catch (Exception openEx) {
            System.err.println("Could not open exported file: " + openEx.getMessage());
        }
    }
}
