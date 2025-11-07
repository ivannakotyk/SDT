package com.ivanka.audioeditor.client.core.modules;

import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.mediator.AbstractColleague;
import com.ivanka.audioeditor.client.ui.EditorContext;

public class NotificationModule extends AbstractColleague {
    private final EditorContext ctx;
    public NotificationModule(EditorContext ctx) { this.ctx = ctx; }

    @Override public String key() { return "Notification"; }

    @Override
    public void receive(EditorEvent e) {
        switch (e.type) {
            case NOTIFY_INFO -> ctx.alertInfo(String.valueOf(e.get("info")));
            case NOTIFY_WARN -> ctx.alertWarn(String.valueOf(e.get("warn")));
            case NOTIFY_ERROR -> ctx.alertError(String.valueOf(e.get("error")));
            case AUDIO_IMPORTED -> ctx.toast("Audio imported");
            default -> {}
        }
    }
}
