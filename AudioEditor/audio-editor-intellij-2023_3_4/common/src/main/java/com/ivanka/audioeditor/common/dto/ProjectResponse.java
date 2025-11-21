package com.ivanka.audioeditor.common.dto;

public record ProjectResponse(
        Long id,
        String projectName,
        Long userId
) { }