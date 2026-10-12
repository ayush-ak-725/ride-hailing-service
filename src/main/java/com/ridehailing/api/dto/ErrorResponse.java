package com.ridehailing.api.dto;

import java.util.List;

/** {@code {"error": {"code": ..., "message": ..., "details": [...]}}} for every failed request. */
public record ErrorResponse(Body error) {

    public record Body(String code, String message, List<String> details) {
    }

    public static ErrorResponse of(String code, String message, List<String> details) {
        return new ErrorResponse(new Body(code, message, details));
    }
}
