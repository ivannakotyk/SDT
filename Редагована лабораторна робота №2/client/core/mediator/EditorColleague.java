package com.ivanka.audioeditor.client.core.mediator;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public interface EditorColleague {
    void setMediator(EditorMediator mediator);
    EditorMediator getMediator();
    void receive(EditorEvent event);
    String key();
}
