package com.ridehailing.config;

import com.ridehailing.domain.CarType;
import com.ridehailing.pricing.TieredRateCard;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Rate cards per car type, bound from {@code pricing.rate-cards} in application.yml. */
@Validated
@ConfigurationProperties("pricing")
public record PricingProperties(@NotEmpty Map<CarType, RateCardProperties> rateCards) {

    public record RateCardProperties(@NotNull BigDecimal minimumFare, @NotEmpty List<TieredRateCard.Slab> slabs) {

        public TieredRateCard toRateCard() {
            return new TieredRateCard(minimumFare, slabs);
        }
    }
}
