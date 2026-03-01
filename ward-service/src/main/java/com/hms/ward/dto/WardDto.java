package com.hms.ward.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardDto {
    private UUID id;
    private String name;
    private String type;
    private Integer capacity;
    private long occupied;
    private long available;
    private long maintenance;
    private Boolean isActive;
}
