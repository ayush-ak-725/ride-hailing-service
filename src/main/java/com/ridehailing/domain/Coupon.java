package com.ridehailing.domain;

import com.ridehailing.pricing.Discount;

public record Coupon(String code, Discount discount) {
}
