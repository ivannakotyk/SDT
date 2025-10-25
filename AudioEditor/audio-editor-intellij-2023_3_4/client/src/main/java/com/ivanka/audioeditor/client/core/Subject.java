package com.ivanka.audioeditor.client.core;

import com.ivanka.audioeditor.client.core.events.EditorEvent;

public interface Subject {
    void attach(Observer o);
    void detach(Observer o);
    void notifyObservers(EditorEvent event);
}
