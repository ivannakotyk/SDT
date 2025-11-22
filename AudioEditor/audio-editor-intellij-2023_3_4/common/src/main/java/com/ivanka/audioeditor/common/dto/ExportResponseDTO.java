package com.ivanka.audioeditor.common.dto;

public record ExportResponseDTO(
        String fileName,
        String downloadUrl
) {}