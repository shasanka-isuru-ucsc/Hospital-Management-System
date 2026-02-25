package com.hms.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private PaginationMeta meta;
    private ErrorDetail error;
    private String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, null, message);
    }

    public static <T> ApiResponse<T> success(T data, PaginationMeta meta) {
        return new ApiResponse<>(true, data, meta, null, null);
    }

    public static ApiResponse<Void> error(String code, String message, int statusCode) {
        return new ApiResponse<>(false, null, null, new ErrorDetail(code, message, statusCode), null);
    }
}
