package com.ivanka.audioeditor.client.model;

import com.ivanka.audioeditor.client.core.Observable;
import com.ivanka.audioeditor.client.core.Observer;
import java.util.ArrayList;
import java.util.List;

public class ProjectModel implements Observable {
    public static class Track {
        public long id; public String name;
        public List<Segment> segments = new ArrayList<>();
    }
    public static class Segment {
        public long id; public String wavPath;
        public double start, end;
    }

    private final List<Observer> observers = new ArrayList<>();
    public long id;
    public String name;
    public List<Track> tracks = new ArrayList<>();

    @Override public void addObserver(Observer o) { observers.add(o); }
    @Override public void removeObserver(Observer o) { observers.remove(o); }
    @Override public void notifyObservers() { observers.forEach(o -> o.update(this)); }
}
