package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.model.SegmentEntity;
import com.ivanka.audioeditor.server.repo.TrackRepository;
import com.ivanka.audioeditor.server.repo.SegmentRepository;
import com.ivanka.audioeditor.server.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/play")
@CrossOrigin
public class PlaybackController {

    private final ProjectService projects;
    private final TrackRepository tracks;
    private final SegmentRepository segments;

    public PlaybackController(ProjectService projects, TrackRepository tracks, SegmentRepository segments) {
        this.projects = projects;
        this.tracks = tracks;
        this.segments = segments;
    }

    @GetMapping("/project/{projectId}")
    public Map<String, Object> play(@PathVariable Long projectId) {
        ProjectEntity project = projects.get(projectId);

        List<TrackEntity> trackList = tracks.findByProjectOrderByTrackOrderAsc(project);

        List<Map<String, Object>> trackDtos = new ArrayList<>();
        for (TrackEntity track : trackList) {
            Map<String, Object> trackDto = new LinkedHashMap<>();
            trackDto.put("trackId", track.getId());
            trackDto.put("trackName", track.getTrackName());
            trackDto.put("trackOrder", track.getTrackOrder());
            trackDto.put("volume", track.getVolume());
            trackDto.put("muted", track.isMuted());

            List<SegmentEntity> segmentList = segments.findByTrackOrderByStartTimeSecAsc(track);
            List<Map<String, Object>> segmentDtos = new ArrayList<>();
            for (SegmentEntity seg : segmentList) {
                Map<String, Object> segMap = new LinkedHashMap<>();
                segMap.put("segmentId", seg.getId());
                segMap.put("start", seg.getStartTimeSec());
                segMap.put("end", seg.getEndTimeSec());
                segMap.put("wavPath", seg.getAudioFile() != null ? seg.getAudioFile().getFilePath() : null);
                segmentDtos.add(segMap);
            }

            trackDto.put("segments", segmentDtos);
            trackDtos.add(trackDto);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", project.getId());
        result.put("projectName", project.getProjectName());
        result.put("tracks", trackDtos);
        result.put("status", "ready");

        return result;
    }
}
