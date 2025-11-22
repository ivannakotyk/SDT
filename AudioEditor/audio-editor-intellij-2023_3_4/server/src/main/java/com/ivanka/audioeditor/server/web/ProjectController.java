package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.common.dto.CreateProjectRequest;
import com.ivanka.audioeditor.common.dto.FullProjectDTO;
import com.ivanka.audioeditor.common.dto.ProjectResponse;
import com.ivanka.audioeditor.common.dto.TrackDTO;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.repo.ProjectRepository;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.service.DTOMapper;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin
public class ProjectController {

    private final ProjectService service;
    private final TrackRepository tracks;
    private final ProjectRepository projectRepo;
    private final DTOMapper mapper;

    public ProjectController(ProjectService service, TrackRepository tracks, ProjectRepository projectRepo, DTOMapper mapper) {
        this.service = service;
        this.tracks = tracks;
        this.projectRepo = projectRepo;
        this.mapper = mapper;
    }

    @PostMapping
    public ProjectResponse create(@RequestBody CreateProjectRequest req) {
        var project = service.createProject(req.userId(), req.projectName());
        return mapper.toProjectResponse(project);
    }

    @GetMapping("/by-user/{userId}")
    public List<ProjectResponse> byUser(@PathVariable("userId") Long userId) {
        return service.listByUser(userId).stream()
                .map(mapper::toProjectResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{projectId}")
    public FullProjectDTO get(@PathVariable("projectId") Long projectId) {
        ProjectEntity project = service.get(projectId);
        return mapper.toFullProjectDTO(project);
    }

    @PostMapping("/{projectId}/tracks")
    public TrackDTO addTrack(@PathVariable("projectId") Long projectId, @RequestParam("name") String name) {
        TrackEntity track = service.addTrack(projectId, name);
        return mapper.toTrackDTO(track);
    }

    @GetMapping("/{projectId}/tracks")
    public List<TrackDTO> getTracks(@PathVariable("projectId") Long projectId) {
        ProjectEntity p = service.get(projectId);
        return tracks.findByProjectOrderByTrackOrderAsc(p).stream()
                .map(mapper::toTrackDTO)
                .collect(Collectors.toList());
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