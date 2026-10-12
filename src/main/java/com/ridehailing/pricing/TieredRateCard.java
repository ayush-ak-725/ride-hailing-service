package com.ridehailing.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Marginal (slab-wise) tiered pricing, like income-tax brackets: each kilometre is charged at the
 * rate of the slab it falls in, so the fare never decreases as distance grows. The result is
 * floored at {@code minimumFare}.
 *
 * <p>Slabs must be in ascending order of {@code upToKm}; the last slab must be unbounded.
 */
public final class TieredRateCard implements RateCard {

    /** One pricing slab. {@code upToKm == null} means "and beyond". */
    public record Slab(BigDecimal upToKm, BigDecimal ratePerKm) {
        public Slab {
            Objects.requireNonNull(ratePerKm, "ratePerKm");
            if (ratePerKm.signum() < 0) {
                throw new IllegalArgumentException("ratePerKm must not be negative");
            }
            if (upToKm != null && upToKm.signum() <= 0) {
                throw new IllegalArgumentException("upToKm must be positive");
            }
        }

        public static Slab upTo(String km, String ratePerKm) {
            return new Slab(new BigDecimal(km), new BigDecimal(ratePerKm));
        }

        public static Slab beyond(String ratePerKm) {
            return new Slab(null, new BigDecimal(ratePerKm));
        }
    }

    private final BigDecimal minimumFare;
    private final List<Slab> slabs;

    public TieredRateCard(BigDecimal minimumFare, List<Slab> slabs) {
        Objects.requireNonNull(minimumFare, "minimumFare");
        if (minimumFare.signum() < 0) {
            throw new IllegalArgumentException("minimumFare must not be negative");
        }
        if (slabs == null || slabs.isEmpty()) {
            throw new IllegalArgumentException("At least one slab is required");
        }
        validateSlabOrder(slabs);
        this.minimumFare = minimumFare;
        this.slabs = List.copyOf(slabs);
    }

    private static void validateSlabOrder(List<Slab> slabs) {
        BigDecimal previousLimit = BigDecimal.ZERO;
        for (int i = 0; i < slabs.size(); i++) {
            BigDecimal limit = slabs.get(i).upToKm();
            boolean isLast = i == slabs.size() - 1;
            if (limit == null && !isLast) {
                throw new IllegalArgumentException("Only the last slab may be unbounded");
            }
            if (limit == null) {
                return;
            }
            if (limit.compareTo(previousLimit) <= 0) {
                throw new IllegalArgumentException("Slab limits must be strictly ascending");
            }
            previousLimit = limit;
        }
        throw new IllegalArgumentException("The last slab must be unbounded");
    }

    @Override
    public BigDecimal fareFor(BigDecimal distanceKm) {
        if (distanceKm.signum() < 0) {
            throw new IllegalArgumentException("distanceKm must not be negative");
        }
        BigDecimal fare = BigDecimal.ZERO;
        BigDecimal slabStart = BigDecimal.ZERO;
        for (Slab slab : slabs) {
            if (distanceKm.compareTo(slabStart) <= 0) {
                break;
            }
            BigDecimal slabEnd = slab.upToKm() == null ? distanceKm : slab.upToKm().min(distanceKm);
            fare = fare.add(slabEnd.subtract(slabStart).multiply(slab.ratePerKm()));
            slabStart = slabEnd;
        }
        return fare.max(minimumFare);
    }
}
