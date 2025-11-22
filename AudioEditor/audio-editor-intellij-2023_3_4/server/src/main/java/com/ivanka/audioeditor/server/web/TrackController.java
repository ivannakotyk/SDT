package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.common.dto.TrackDTO;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.service.DTOMapper;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tracks")
@CrossOrigin
public class TrackController {

    private final TrackRepository repo;
    private final ProjectService projects;
    private final DTOMapper mapper;

    public TrackController(TrackRepository repo, ProjectService projects, DTOMapper mapper) {
        this.repo = repo;
        this.projects = projects;
        this.mapper = mapper;
    }

    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<?> byProject(@PathVariable Long projectId) {
        try {
            ProjectEntity p = projects.get(projectId);
            List<TrackEntity> list = repo.findByProjectOrderByTrackOrderAsc(p);
            List<TrackDTO> dtos = list.stream()
                    .map(mapper::toTrackDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/{trackId}")
    public void deleteTrack(@PathVariable Long trackId) {
        repo.deleteById(trackId);
        System.out.println("Deleted track ID: " + trackId);
    }
}