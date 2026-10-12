package com.ridehailing.api.dto;

import com.ridehailing.domain.Coupon;
import com.ridehailing.pricing.PercentageDiscount;

import java.math.BigDecimal;

public record CouponResponse(String code, BigDecimal percentOff, BigDecimal maxDiscount) {

    public static CouponResponse from(Coupon coupon, PercentageDiscount discount) {
        return new CouponResponse(coupon.code(), discount.percent(), discount.maxAmount());
    }
}
