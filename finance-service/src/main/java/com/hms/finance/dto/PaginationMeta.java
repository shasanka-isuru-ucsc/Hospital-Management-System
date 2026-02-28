package com.hms.finance.dto;

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
    private Double totalPendingAmount;

    public PaginationMeta(int page, int limit, long total, int totalPages) {
        this.page = page;
        this.limit = limit;
        this.total = total;
        this.totalPages = totalPages;
    }
}
