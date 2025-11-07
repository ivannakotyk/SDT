package com.ivanka.audioeditor.client.core;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public interface Observer {
    void update(EditorEvent event);
}