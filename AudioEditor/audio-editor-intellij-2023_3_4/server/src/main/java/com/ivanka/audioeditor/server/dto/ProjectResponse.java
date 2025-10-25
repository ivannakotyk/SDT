package com.ivanka.audioeditor.server.dto;

public record ProjectResponse(
        Long id,
        String projectName,
        Long userId
) {}
