package com.ridehailing.pricing;

import com.ridehailing.domain.Location;

import java.math.BigDecimal;

public class NoSurge implements SurgePricing {

    @Override
    public BigDecimal multiplierAt(Location pickup) {
        return BigDecimal.ONE;
    }
}
