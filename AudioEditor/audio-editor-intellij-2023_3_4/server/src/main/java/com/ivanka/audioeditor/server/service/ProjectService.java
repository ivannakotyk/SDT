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
        this.projects = projects;
        this.users = users;
        this.tracks = tracks;
    }

    @Transactional
    public ProjectEntity createProject(Long userId, String requestedName) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));

        List<String> existingNames = projects.findByUser(user).stream()
                .map(ProjectEntity::getProjectName)
                .map(String::trim)
                .toList();

        String finalName = generateUniqueName(requestedName.trim(), existingNames);

        ProjectEntity p = new ProjectEntity(finalName, user);
        return projects.save(p);
    }

    public List<ProjectEntity> listByUser(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));
        return projects.findByUser(user);
    }

    public ProjectEntity get(Long projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: id=" + projectId));
    }

    @Transactional
    public TrackEntity addTrack(Long projectId, String requestedName) {
        ProjectEntity p = get(projectId);

        List<String> existingNames = p.getTracks().stream()
                .map(TrackEntity::getTrackName)
                .map(String::trim)
                .toList();

        String finalName = generateUniqueName(requestedName.trim(), existingNames);

        int order = tracks.nextOrderForProject(p);
        TrackEntity t = new TrackEntity(p, finalName, order);
        return tracks.save(t);
    }

    private String generateUniqueName(String baseName, List<String> existingNames) {
        if (!existingNames.contains(baseName)) return baseName;

        int counter = 2;
        String newName;
        do {
            newName = baseName + " " + counter++;
        } while (existingNames.contains(newName));

        return newName;
    }
}
