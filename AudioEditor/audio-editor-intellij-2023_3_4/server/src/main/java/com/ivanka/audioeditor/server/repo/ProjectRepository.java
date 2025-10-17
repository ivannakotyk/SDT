package com.ivanka.audioeditor.server.repo;

import com.ivanka.audioeditor.server.model.ProjectEntity;
import com.ivanka.audioeditor.server.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    List<ProjectEntity> findByUser(AppUser user);
    boolean existsByProjectNameAndUser(String projectName, AppUser user);
}
