package com.ivanka.audioeditor.common.dto;

import java.util.List;

public record FullProjectDTO(
        Long id,
        String projectName,
        Long userId,
        List<TrackDTO> tracks
) {}