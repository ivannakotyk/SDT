package com.ivanka.audioeditor.client.core.mediator;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public class ConcreteEditorMediator implements EditorMediator {
    private EditorColleague projectModule;
    private EditorColleague trackModule;
    private EditorColleague importModule;
    private EditorColleague waveformModule;
    private EditorColleague playbackModule;
    private EditorColleague exportModule;
    private EditorColleague notificationModule;
    private EditorColleague clipboardModule;

    @Override
    public void register(String key, EditorColleague colleague) {
        colleague.setMediator(this);
        switch (key) {
            case "Project":
                this.projectModule = colleague;
                break;
            case "Track":
                this.trackModule = colleague;
                break;
            case "Import":
                this.importModule = colleague;
                break;
            case "Waveform":
                this.waveformModule = colleague;
                break;
            case "Playback":
                this.playbackModule = colleague;
                break;
            case "Export":
                this.exportModule = colleague;
                break;
            case "Notification":
                this.notificationModule = colleague;
                break;
            case "Clipboard":
                this.clipboardModule = colleague;
                break;
            default:
                System.err.println("Unknown colleague key registered: " + key);
        }
    }

    @Override
    public void onEvent(EditorColleague source, EditorEvent e) {
        switch (e.type) {
            case PROJECT_CREATE_REQUEST, PROJECT_SELECTED -> relay(projectModule, e);
            case TRACK_ADD_REQUEST, TRACKS_REFRESH_REQUEST -> relay(trackModule, e);

            case IMPORT_REQUEST -> relay(importModule, e);
            case AUDIO_IMPORTED -> {
                relay(waveformModule, e);
                relay(trackModule, e);
                relay(notificationModule, e.with("info", "Audio imported"));
            }

            case PLAYBACK_START, PLAYBACK_STOP -> {
                relay(playbackModule, e);
                relay(trackModule, e);
                relay(waveformModule, e);
            }

            case PLAYBACK_PROGRESS, PLAYBACK_FINISHED -> {
                relay(trackModule, e);
            }

            case EXPORT_REQUEST -> relay(exportModule, e);

            case NOTIFY_INFO, NOTIFY_WARN, NOTIFY_ERROR -> relay(notificationModule, e);

            case WAVEFORM_REDRAW -> relay(waveformModule, e);

            case CLIPBOARD_COPY, CLIPBOARD_CUT, CLIPBOARD_PASTE -> relay(clipboardModule, e);

            default -> { /* no-op */ }
        }
    }
    private void relay(EditorColleague tgt, EditorEvent e) {
        if (tgt != null) {
            tgt.receive(e);
        }
    }
}