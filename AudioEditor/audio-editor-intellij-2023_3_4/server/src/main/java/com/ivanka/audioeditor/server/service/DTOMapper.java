package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.common.dto.*;
import com.ivanka.audioeditor.server.model.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class DTOMapper {

    public FullProjectDTO toFullProjectDTO(ProjectEntity project) {
        return new FullProjectDTO(
                project.getId(),
                project.getProjectName(),
                project.getUser().getId(),
                project.getTracks().stream().map(this::toTrackDTO).collect(Collectors.toList())
        );
    }

    public TrackDTO toTrackDTO(TrackEntity track) {
        return new TrackDTO(
                track.getId(),
                track.getTrackName(),
                track.getTrackOrder(),
                track.isMuted(),
                track.getVolume(),
                track.getSegments().stream().map(this::toSegmentDTO).collect(Collectors.toList())
        );
    }

    public SegmentDTO toSegmentDTO(SegmentEntity segment) {
        String path = null;
        String name = "unknown";
        if (segment.getAudioFile() != null) {
            path = "/uploads/" + segment.getAudioFile().getFileName();
            name = segment.getAudioFile().getFileName();
        }
        return new SegmentDTO(
                segment.getId(),
                segment.getStartTimeSec(),
                segment.getEndTimeSec(),
                path,
                name
        );
    }

    public ProjectResponse toProjectResponse(ProjectEntity project) {
        return new ProjectResponse(
                project.getId(),
                project.getProjectName(),
                project.getUser().getId()
        );
    }
}