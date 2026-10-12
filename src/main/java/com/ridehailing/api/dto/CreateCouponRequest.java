package com.ridehailing.api.dto;

import com.ridehailing.pricing.PercentageDiscount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateCouponRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.COUPON_CODE, message = ValidationPatterns.COUPON_CODE_MESSAGE)
        String code,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal percentOff,
        @DecimalMin(value = "0", inclusive = false) BigDecimal maxDiscount) {

    public PercentageDiscount toDiscount() {
        return new PercentageDiscount(percentOff, maxDiscount);
    }
}
