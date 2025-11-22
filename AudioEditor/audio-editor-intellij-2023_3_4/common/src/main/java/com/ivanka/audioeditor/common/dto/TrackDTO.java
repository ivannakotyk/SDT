package com.ivanka.audioeditor.common.dto;

import java.util.List;

public record TrackDTO(
        Long id,
        String name,
        int order,
        boolean isMuted,
        double volume,
        List<SegmentDTO> segments
) {}