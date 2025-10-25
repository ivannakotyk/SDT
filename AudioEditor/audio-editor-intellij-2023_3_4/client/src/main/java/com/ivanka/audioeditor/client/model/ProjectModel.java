package com.ivanka.audioeditor.client.model;

import com.ivanka.audioeditor.client.core.Observer;
import java.util.ArrayList;
import java.util.List;

public class ProjectModel {
    public static class Track {
        public long id;
        public String name;
        public List<Segment> segments = new ArrayList<>();
    }

    public static class Segment {
        public long id;
        public String wavPath;
        public double start, end;
    }

    public long id;
    public String name;
    public List<Track> tracks = new ArrayList<>();
}
