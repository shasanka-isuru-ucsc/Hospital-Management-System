package com.hms.reception.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMeta {
    private int page;
    private int limit;
    private long total;
    private int totalPages;
}
