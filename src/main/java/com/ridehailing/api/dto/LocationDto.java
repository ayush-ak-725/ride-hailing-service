package com.ridehailing.api.dto;

import com.ridehailing.domain.Location;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Grid coordinates in km. Bounded so that values like 1e999 (parsed as Infinity) are rejected. */
public record LocationDto(
        @NotNull @DecimalMin("-1000") @DecimalMax("1000") Double x,
        @NotNull @DecimalMin("-1000") @DecimalMax("1000") Double y) {

    public Location toLocation() {
        return new Location(x, y);
    }

    public static LocationDto from(Location location) {
        return location == null ? null : new LocationDto(location.x(), location.y());
    }
}
