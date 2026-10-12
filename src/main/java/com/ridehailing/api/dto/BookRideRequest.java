package com.ridehailing.api.dto;

import com.ridehailing.domain.CarType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BookRideRequest(
        @NotBlank String userId,
        @NotNull @Valid LocationDto pickup,
        @NotNull CarType carType,
        @Pattern(regexp = ValidationPatterns.COUPON_CODE, message = ValidationPatterns.COUPON_CODE_MESSAGE)
        String couponCode) {
}
