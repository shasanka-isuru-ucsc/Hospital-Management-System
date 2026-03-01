package com.hms.lab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ResultsUpdateRequest {

    @NotEmpty(message = "At least one result is required")
    @Valid
    private List<ResultItem> results;
}
