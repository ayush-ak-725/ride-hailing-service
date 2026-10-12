package com.ridehailing.domain;

/**
 * A point on a flat city grid, in kilometres. Distance is straight-line (Euclidean).
 * Swapping to lat/long would change only this record (haversine in {@link #distanceTo}).
 */
public record Location(double x, double y) {

    public Location {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Location coordinates must be finite numbers");
        }
    }

    public double distanceTo(Location other) {
        return Math.hypot(x - other.x, y - other.y);
    }
}
