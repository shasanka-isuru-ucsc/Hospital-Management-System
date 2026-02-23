package com.hms.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionImageDto {
    private UUID id;
    private UUID sessionId;
    private String fileUrl;
    private String caption;
    private String imageType;
    private LocalDateTime uploadedAt;
}
