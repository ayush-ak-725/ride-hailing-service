package com.ridehailing.pricing;

import com.ridehailing.domain.Location;

import java.math.BigDecimal;

/** Supplies the surge multiplier (always >= 1) for a booking at the given pickup point. */
public interface SurgePricing {

    BigDecimal multiplierAt(Location pickup);
}
