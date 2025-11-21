package com.ivanka.audioeditor.common.dto;

public record CreateProjectRequest(
        Long userId,
        String projectName
) { }
