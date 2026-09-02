package com.classhub.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {
    }
}
