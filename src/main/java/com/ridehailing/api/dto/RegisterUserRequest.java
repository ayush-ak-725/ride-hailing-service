package com.ridehailing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally prefixed with +")
        String phone) {
}
