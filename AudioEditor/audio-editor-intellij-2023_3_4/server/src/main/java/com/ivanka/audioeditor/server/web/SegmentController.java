package com.ivanka.audioeditor.server.web;

import com.ivanka.audioeditor.server.model.*;
import com.ivanka.audioeditor.server.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

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

    @PostMapping("/import/{trackId}")
    public SegmentEntity importAudio(@PathVariable Long trackId,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        TrackEntity track = tracks.findById(trackId)
                .orElseThrow(() -> new RuntimeException("Track not found"));

        File savedFile = saveFileToStorage(file);

        AudioFileEntity audioEntity = new AudioFileEntity();
        audioEntity.setFileName(savedFile.getName());
        audioEntity.setFilePath(savedFile.getAbsolutePath());
        audioEntity.setFileFormat("wav");
        audioFiles.save(audioEntity);

        SegmentEntity segment = new SegmentEntity();
        segment.setTrack(track);
        segment.setAudioFile(audioEntity);
        segment.setStartTimeSec(0.0);
        segment.setEndTimeSec(0.0);

        return segments.save(segment);
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<?> updateSegmentAudio(@PathVariable Long id,
                                                @RequestParam("file") MultipartFile file) {
        try {
            System.out.println("--- SAVE REQUEST for Segment " + id + " ---");
            SegmentEntity segment = segments.findById(id)
                    .orElseThrow(() -> new RuntimeException("Segment not found"));

            File savedFile = saveFileToStorage(file);

            AudioFileEntity audio = segment.getAudioFile();
            if (audio == null) {
                audio = new AudioFileEntity();
                segment.setAudioFile(audio);
            }
            audio.setFileName(savedFile.getName());
            audio.setFilePath(savedFile.getAbsolutePath());

            audioFiles.save(audio);
            segments.save(segment);

            System.out.println("Database updated with new file: " + savedFile.getName());
            return ResponseEntity.ok().body("Saved");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    private File saveFileToStorage(MultipartFile file) throws IOException {
        File dir = new File(uploadsDir).getAbsoluteFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("Created uploads directory: " + dir.getAbsolutePath() + " -> " + created);
        }

        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".") ? original.substring(original.lastIndexOf(".")) : ".wav";
        String uniqueName = UUID.randomUUID().toString() + ext;

        File dest = new File(dir, uniqueName);
        file.transferTo(dest);

        System.out.println("File saved physically to: " + dest.getAbsolutePath());
        return dest;
    }

    @GetMapping("/by-track/{trackId}")
    public List<SegmentEntity> byTrack(@PathVariable Long trackId) {
        TrackEntity t = tracks.findById(trackId).orElseThrow();
        return segments.findByTrackOrderByStartTimeSecAsc(t);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        segments.deleteById(id);
    }
}