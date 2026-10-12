package com.ridehailing.pricing;

import com.ridehailing.domain.Ride;

import java.math.BigDecimal;
import java.time.Instant;

public interface CancellationFeePolicy {

    BigDecimal feeFor(Ride ride, Instant cancelledAt);
}
