package com.ridehailing.api.dto;

import com.ridehailing.domain.CarType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDriverRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally prefixed with +")
        String phone,
        @NotBlank @Size(max = 20) String plateNumber,
        @NotNull CarType carType,
        @NotNull @Valid LocationDto location,
        @Schema(description = "Seed rating used by the highest-rated matching strategy; defaults to 5.0")
        @DecimalMin("1.0") @DecimalMax("5.0") Double rating) {

    private static final double DEFAULT_RATING = 5.0;

    public double ratingOrDefault() {
        return rating == null ? DEFAULT_RATING : rating;
    }
}
