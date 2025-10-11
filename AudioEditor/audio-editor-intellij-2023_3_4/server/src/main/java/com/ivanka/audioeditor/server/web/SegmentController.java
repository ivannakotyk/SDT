package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.model.*;
import com.ivanka.audioeditor.server.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/segments")
@CrossOrigin
public class SegmentController {

    private final SegmentRepository segments;
    private final TrackRepository tracks;
    private final AudioFileRepository audioFiles;

    @Value("${storage.uploadsDir:storage/uploads}")
    private String uploadsDir;

    public SegmentController(SegmentRepository segments,
                             TrackRepository tracks,
                             AudioFileRepository audioFiles) {
        this.segments = segments;
        this.tracks = tracks;
        this.audioFiles = audioFiles;
    }

    @GetMapping("/by-track/{trackId}")
    public List<SegmentEntity> byTrack(@PathVariable Long trackId) {
        TrackEntity t = tracks.findById(trackId).orElseThrow();
        return segments.findByTrackOrderByStartTimeSecAsc(t);
    }

    @PostMapping("/import/{trackId}")
    public SegmentEntity importAudio(@PathVariable Long trackId,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        TrackEntity track = tracks.findById(trackId)
                .orElseThrow(() -> new RuntimeException("Track not found"));

        File dir = new File(uploadsDir);
        if (!dir.exists()) dir.mkdirs();

        File dest = new File(dir, file.getOriginalFilename());
        file.transferTo(dest);

        SegmentEntity segment = segments.findByTrackOrderByStartTimeSecAsc(track)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No segment found for this track"));

        AudioFileEntity audio = segment.getAudioFile();
        audio.setFileName(file.getOriginalFilename());
        audio.setFilePath(dest.getAbsolutePath());
        audio.setFileFormat(file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1));
        audioFiles.save(audio);

        segment.setStartTimeSec(0.0);
        segment.setEndTimeSec(0.0);
        segments.save(segment);

        System.out.println("🎧 Imported audio into track " + track.getTrackName());
        return segment;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        segments.deleteById(id);
    }
}
