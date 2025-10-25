package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    public ResponseEntity<?> byProject(@PathVariable Long projectId) {
        try {
            ProjectEntity p = projects.get(projectId);
            List<TrackEntity> list = repo.findByProjectOrderByTrackOrderAsc(p);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", 404,
                            "error", "Project not found",
                            "message", e.getMessage(),
                            "projectId", projectId
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", 500,
                            "error", "Server error while loading tracks",
                            "message", e.getMessage(),
                            "projectId", projectId
                    ));
        }
    }
}
