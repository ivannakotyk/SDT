package com.ivanka.audioeditor.client.core.events;

import java.util.HashMap;
import java.util.Map;

public class EditorEvent {
    public final EditorEventType type;
    public final Map<String, Object> data = new HashMap<>();

    public EditorEvent(EditorEventType type) {
        this.type = type;
    }

    public EditorEvent with(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        Object val = data.get(key);
        if (val == null) return defaultValue;
        try {
            return (T) val;
        } catch (ClassCastException ex) {
            return defaultValue;
        }
    }

}
