package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracks")
@CrossOrigin
public class TrackController {

    private final TrackRepository repo;
    private final ProjectService projects;

    public TrackController(TrackRepository repo, ProjectService projects) {
        this.repo = repo;
        this.projects = projects;
    }

    @GetMapping("/by-project/{projectId}")
    public List<TrackEntity> byProject(@PathVariable Long projectId) {
        ProjectEntity p = projects.get(projectId);
        return repo.findByProjectOrderByTrackOrderAsc(p);
    }
}
