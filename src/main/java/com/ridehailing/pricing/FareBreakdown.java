package com.ridehailing.pricing;

import java.math.BigDecimal;

/**
 * Itemised fare. Every money field is rounded to paise, and
 * {@code total == surgedFare - discount} holds exactly.
 */
public record FareBreakdown(
        BigDecimal distanceKm,
        BigDecimal baseFare,
        BigDecimal surgeMultiplier,
        BigDecimal surgedFare,
        BigDecimal discount,
        BigDecimal total) {
}
