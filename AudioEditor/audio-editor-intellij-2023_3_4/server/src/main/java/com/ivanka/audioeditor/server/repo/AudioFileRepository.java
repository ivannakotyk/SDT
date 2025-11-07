package com.ivanka.audioeditor.server.repo;
import com.ivanka.audioeditor.server.model.AudioFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AudioFileRepository extends JpaRepository<AudioFileEntity, Long> {}