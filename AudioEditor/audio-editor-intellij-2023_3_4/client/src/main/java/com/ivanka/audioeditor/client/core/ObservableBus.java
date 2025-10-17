package com.ivanka.audioeditor.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ObservableBus {
    private final List<BiConsumer<String,Object>> listeners = new ArrayList<>();
    public void subscribe(BiConsumer<String,Object> l){ listeners.add(l); }
    public void emit(String event, Object payload){ for(var l: listeners) l.accept(event,payload); }
}
