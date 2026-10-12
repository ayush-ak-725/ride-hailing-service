package com.ridehailing.config;

import com.ridehailing.domain.CarType;
import com.ridehailing.matching.DriverMatchingStrategy;
import com.ridehailing.matching.HighestRatedDriverStrategy;
import com.ridehailing.matching.NearestDriverStrategy;
import com.ridehailing.pricing.CancellationFeePolicy;
import com.ridehailing.pricing.FareCalculator;
import com.ridehailing.pricing.GracePeriodCancellationFeePolicy;
import com.ridehailing.pricing.NoSurge;
import com.ridehailing.pricing.RateCard;
import com.ridehailing.pricing.SurgePricing;
import com.ridehailing.pricing.UtilizationSurgePricing;
import com.ridehailing.repository.DriverRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/** Wires the pluggable strategies. Pricing and matching classes stay free of Spring. */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FareCalculator fareCalculator(PricingProperties pricing) {
        Map<CarType, RateCard> rateCards = new EnumMap<>(CarType.class);
        pricing.rateCards().forEach((carType, card) -> rateCards.put(carType, card.toRateCard()));
        return new FareCalculator(rateCards);
    }

    @Bean
    public DriverMatchingStrategy driverMatchingStrategy(RideProperties ride) {
        return switch (ride.matchingStrategy()) {
            case NEAREST -> new NearestDriverStrategy();
            case HIGHEST_RATED -> new HighestRatedDriverStrategy();
        };
    }

    @Bean
    public SurgePricing surgePricing(PricingProperties pricing, RideProperties ride, DriverRepository drivers) {
        PricingProperties.Surge surge = pricing.surge();
        if (!surge.enabled() || surge.tiers() == null || surge.tiers().isEmpty()) {
            return new NoSurge();
        }
        return new UtilizationSurgePricing(drivers, ride.searchRadiusKm(), surge.tiers());
    }

    @Bean
    public CancellationFeePolicy cancellationFeePolicy(PricingProperties pricing) {
        return new GracePeriodCancellationFeePolicy(
                pricing.cancellation().gracePeriod(), pricing.cancellation().fees());
    }
}
