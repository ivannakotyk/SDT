package com.ivanka.audioeditor.common.dto;

public record SegmentDTO(
        Long id,
        double startTime,
        double endTime,
        String wavPath,
        String name
) {}