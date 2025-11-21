package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.common.dto.CreateProjectRequest;
import com.ivanka.audioeditor.common.dto.ProjectResponse;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.repo.ProjectRepository;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin
public class ProjectController {

    private final ProjectService service;
    private final TrackRepository tracks;
    private final ProjectRepository projectRepo;

    public ProjectController(ProjectService service, TrackRepository tracks, ProjectRepository projectRepo) {
        this.service = service;
        this.tracks = tracks;
        this.projectRepo = projectRepo;
    }

    @PostMapping
    public ProjectResponse create(@RequestBody CreateProjectRequest req) {
        var project = service.createProject(req.userId(), req.projectName());
        return new ProjectResponse(project.getId(), project.getProjectName(), project.getUser().getId());
    }

    @GetMapping("/by-user/{userId}")
    public List<ProjectEntity> byUser(@PathVariable("userId") Long userId) {
        return service.listByUser(userId);
    }

    @GetMapping("/{projectId}")
    public ProjectEntity get(@PathVariable("projectId") Long projectId) {
        return service.get(projectId);
    }

    @PostMapping("/{projectId}/tracks")
    public TrackEntity addTrack(@PathVariable("projectId") Long projectId, @RequestParam("name") String name) {
        return service.addTrack(projectId, name);
    }

    @GetMapping("/{projectId}/tracks")
    public List<TrackEntity> getTracks(@PathVariable("projectId") Long projectId) {
        ProjectEntity p = service.get(projectId);
        return tracks.findByProjectOrderByTrackOrderAsc(p);
    }

    @DeleteMapping("/{projectId}")
    public void deleteProject(@PathVariable Long projectId) {
        projectRepo.deleteById(projectId);
        System.out.println("Deleted project ID: " + projectId);
    }
    @PutMapping("/{projectId}")
    public void renameProject(@PathVariable Long projectId, @RequestParam("name") String newName) {
        ProjectEntity project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (newName != null && !newName.trim().isEmpty()) {
            project.setProjectName(newName.trim());
            projectRepo.save(project);
            System.out.println("Renamed project " + projectId + " to: " + newName);
        }
    }
}