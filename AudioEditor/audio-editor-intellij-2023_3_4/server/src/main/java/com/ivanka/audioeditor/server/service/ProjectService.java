package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.server.model.*;
import com.ivanka.audioeditor.server.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final AppUserRepository users;
    private final TrackRepository tracks;

    public ProjectService(ProjectRepository projects, AppUserRepository users, TrackRepository tracks) {
        this.projects = projects; this.users = users; this.tracks = tracks;
    }

    @Transactional
    public ProjectEntity createProject(Long userId, String name) {
        AppUser user = users.findById(userId).orElseThrow();
        ProjectEntity p = new ProjectEntity(name, user);
        return projects.save(p);
    }

    public List<ProjectEntity> listByUser(Long userId) {
        AppUser user = users.findById(userId).orElseThrow();
        return projects.findByUser(user);
    }

    public ProjectEntity get(Long projectId) { return projects.findById(projectId).orElseThrow(); }

    @Transactional
    public TrackEntity addTrack(Long projectId, String name) {
        ProjectEntity p = get(projectId);
        int order = p.getTracks().size();
        TrackEntity t = new TrackEntity(p, name, order);
        p.getTracks().add(t);
        projects.save(p);
        return t;
    }
}
