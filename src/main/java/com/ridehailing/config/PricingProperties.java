package com.ridehailing.config;

import com.ridehailing.domain.CarType;
import com.ridehailing.pricing.TieredRateCard;
import com.ridehailing.pricing.UtilizationSurgePricing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Rate cards, surge and cancellation settings, bound from {@code pricing.*} in application.yml. */
@Validated
@ConfigurationProperties("pricing")
public record PricingProperties(
        @NotEmpty Map<CarType, RateCardProperties> rateCards,
        @NotNull @Valid Surge surge,
        @NotNull @Valid Cancellation cancellation) {

    /** @param tiers applied when enabled; see {@link UtilizationSurgePricing} */
    public record Surge(boolean enabled, List<UtilizationSurgePricing.Tier> tiers) {
    }

    public record Cancellation(@NotNull Duration gracePeriod, @NotEmpty Map<CarType, BigDecimal> fees) {
    }

    public record RateCardProperties(@NotNull BigDecimal minimumFare, @NotEmpty List<TieredRateCard.Slab> slabs) {

        public TieredRateCard toRateCard() {
            return new TieredRateCard(minimumFare, slabs);
        }
    }
}
