package com.ivanka.audioeditor.client.core.mediator;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public interface EditorMediator {
    void register(String key, EditorColleague colleague);
    void onEvent(EditorColleague source, EditorEvent event);
}
