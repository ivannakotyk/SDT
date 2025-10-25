package com.ivanka.audioeditor.client.core.observers;

import com.ivanka.audioeditor.client.core.Observer;
import com.ivanka.audioeditor.client.core.events.EditorEvent;
import com.ivanka.audioeditor.client.core.events.EditorEventType;
import com.ivanka.audioeditor.client.ui.EditorContext;

public class NotificationObserver implements Observer {
    private final EditorContext ctx;

    public NotificationObserver(EditorContext ctx) { this.ctx = ctx; }

    @Override
    public void update(EditorEvent e) {
        switch (e.type) {
            case NOTIFY_INFO -> ctx.alertInfo(e.get("message"));
            case NOTIFY_WARN -> ctx.alertWarn(e.get("message"));
            case NOTIFY_ERROR -> ctx.alertError(e.get("message"));
            default -> {}
        }
    }
}
