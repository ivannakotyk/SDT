package com.ivanka.audioeditor.server.repo;
import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.TrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
public interface TrackRepository extends JpaRepository<TrackEntity, Long> {
    List<TrackEntity> findByProjectOrderByTrackOrderAsc(ProjectEntity project);

    @Query("select coalesce(max(t.trackOrder), 0) + 1 from TrackEntity t where t.project = :project")
    int nextOrderForProject(@Param("project") ProjectEntity project);
}
