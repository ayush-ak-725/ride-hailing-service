package com.ridehailing.config;

import com.ridehailing.domain.CarType;
import com.ridehailing.matching.DriverMatchingStrategy;
import com.ridehailing.matching.HighestRatedDriverStrategy;
import com.ridehailing.matching.NearestDriverStrategy;
import com.ridehailing.pricing.FareCalculator;
import com.ridehailing.pricing.NoSurge;
import com.ridehailing.pricing.RateCard;
import com.ridehailing.pricing.SurgePricing;
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
    public SurgePricing surgePricing() {
        return new NoSurge();
    }
}
