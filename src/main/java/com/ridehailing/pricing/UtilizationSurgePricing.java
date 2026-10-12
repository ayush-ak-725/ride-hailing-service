package com.ridehailing.pricing;

import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.repository.DriverRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Surge from local supply pressure: the share of cabs near the pickup that are already on a ride.
 * The highest tier whose threshold is met applies; below every threshold there is no surge.
 *
 * <p>Utilization is a proxy for demand that needs no extra state. A truer signal would count
 * recent booking requests per area in a sliding window.
 */
public class UtilizationSurgePricing implements SurgePricing {

    /** Applies {@code multiplier} when at least {@code minUtilization} (0..1) of nearby cabs are busy. */
    public record Tier(double minUtilization, BigDecimal multiplier) {
        public Tier {
            Objects.requireNonNull(multiplier, "multiplier");
            if (minUtilization < 0 || minUtilization > 1) {
                throw new IllegalArgumentException("minUtilization must be between 0 and 1");
            }
            if (multiplier.compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException("multiplier must be at least 1");
            }
        }
    }

    private final DriverRepository drivers;
    private final double radiusKm;
    private final List<Tier> tiersHighestFirst;

    public UtilizationSurgePricing(DriverRepository drivers, double radiusKm, List<Tier> tiers) {
        this.drivers = drivers;
        this.radiusKm = radiusKm;
        this.tiersHighestFirst = tiers.stream()
                .sorted(Comparator.comparingDouble(Tier::minUtilization).reversed())
                .toList();
    }

    @Override
    public BigDecimal multiplierAt(Location pickup) {
        List<Driver> nearby = drivers.findAllWithin(pickup, radiusKm);
        if (nearby.isEmpty()) {
            return BigDecimal.ONE;
        }
        long busy = nearby.stream().filter(driver -> !driver.isAvailable()).count();
        double utilization = (double) busy / nearby.size();
        return tiersHighestFirst.stream()
                .filter(tier -> utilization >= tier.minUtilization())
                .map(Tier::multiplier)
                .findFirst()
                .orElse(BigDecimal.ONE);
    }
}
