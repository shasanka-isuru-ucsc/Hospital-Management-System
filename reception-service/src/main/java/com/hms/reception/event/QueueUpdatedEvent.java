package com.hms.reception.event;

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
public class QueueUpdatedEvent {
    private UUID tokenId;
    private int tokenNumber;
    private String patientName;
    private String queueType;
    private String status;
    private UUID doctorId;
    private LocalDateTime updatedAt;
}
