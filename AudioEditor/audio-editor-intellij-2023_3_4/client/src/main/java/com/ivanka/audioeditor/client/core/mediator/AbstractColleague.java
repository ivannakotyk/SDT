package com.ivanka.audioeditor.client.core.mediator;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public abstract class AbstractColleague implements EditorColleague {
    protected EditorMediator mediator;

    @Override public void setMediator(EditorMediator mediator) { this.mediator = mediator; }
    @Override public EditorMediator getMediator() { return mediator; }

    protected final void send(EditorEvent e) {
        if (mediator != null) mediator.onEvent(this, e);
    }

    @Override public abstract void receive(EditorEvent event);
    @Override public abstract String key();
}
