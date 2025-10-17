package com.ivanka.audioeditor.server.repo;

import com.ivanka.audioeditor.server.model.SegmentEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SegmentRepository extends JpaRepository<SegmentEntity, Long> {

    List<SegmentEntity> findByTrackOrderByStartTimeSecAsc(TrackEntity track);

    @Query("""
        SELECT s FROM SegmentEntity s
        WHERE s.track.project = :project
        ORDER BY s.track.trackOrder ASC, s.startTimeSec ASC
    """)
    List<SegmentEntity> findByProjectOrdered(ProjectEntity project);
}
