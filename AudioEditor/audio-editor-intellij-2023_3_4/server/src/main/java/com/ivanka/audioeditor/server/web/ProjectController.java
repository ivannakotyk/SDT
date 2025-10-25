package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.dto.CreateProjectRequest;
import com.ivanka.audioeditor.server.dto.ProjectResponse;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
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

    public ProjectController(ProjectService service, TrackRepository tracks) {
        this.service = service;
        this.tracks = tracks;
    }

    @PostMapping
    public ProjectResponse create(@RequestBody CreateProjectRequest req) {
        var project = service.createProject(req.userId(), req.projectName());
        return new ProjectResponse(
                project.getId(),
                project.getProjectName(),
                project.getUser().getId()
        );
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
    public TrackEntity addTrack(
            @PathVariable("projectId") Long projectId,
            @RequestParam("name") String name
    ) {
        return service.addTrack(projectId, name);
    }

    @GetMapping("/{projectId}/tracks")
    public List<TrackEntity> getTracks(@PathVariable("projectId") Long projectId) {
        ProjectEntity p = service.get(projectId);
        return tracks.findByProjectOrderByTrackOrderAsc(p);
    }
}