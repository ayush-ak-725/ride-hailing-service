package com.ridehailing.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param searchRadiusKm   how far from the pickup point to look for drivers
 * @param matchingStrategy how to choose among eligible drivers
 */
@Validated
@ConfigurationProperties("ride")
public record RideProperties(@Positive double searchRadiusKm, @NotNull MatchingStrategyType matchingStrategy) {

    public enum MatchingStrategyType {
        NEAREST,
        HIGHEST_RATED
    }
}
