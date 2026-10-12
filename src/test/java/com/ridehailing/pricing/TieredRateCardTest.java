package com.ridehailing.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.ridehailing.pricing.PricingFixtures.beyond;
import static com.ridehailing.pricing.PricingFixtures.upTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieredRateCardTest {

    /** Same tiers as the hatchback card but no minimum, so the slab arithmetic is visible. */
    private static final TieredRateCard TIERS_ONLY = new TieredRateCard(BigDecimal.ZERO, List.of(
            upTo("2", "10"), upTo("5", "8"), beyond("5")));

    @ParameterizedTest(name = "{0} km -> {1}")
    @CsvSource({
            "0,    0",
            "1,    10",    // inside first slab
            "2,    20",    // exactly at first boundary
            "2.5,  24",    // 20 + 0.5*8
            "5,    44",    // 20 + 3*8, exactly at second boundary
            "6,    49",    // 44 + 1*5
            "10,   69",    // 44 + 5*5
    })
    void chargesEachKilometreAtTheRateOfItsSlab(String distanceKm, String expectedFare) {
        assertThat(TIERS_ONLY.fareFor(new BigDecimal(distanceKm)))
                .isEqualByComparingTo(expectedFare);
    }

    @ParameterizedTest(name = "{0} km -> {1}")
    @CsvSource({
            "0,    50",
            "2,    50",    // tiered 20, floored
            "6,    50",    // tiered 49, floored
            "6.2,  50",    // tiered exactly 50
            "7,    54",    // tiered 54, above minimum
    })
    void floorsShortRidesAtTheMinimumFare(String distanceKm, String expectedFare) {
        assertThat(PricingFixtures.HATCHBACK.fareFor(new BigDecimal(distanceKm)))
                .isEqualByComparingTo(expectedFare);
    }

    @Test
    void fareNeverDecreasesAsDistanceGrows() {
        BigDecimal previous = BigDecimal.ZERO;
        for (int tenthsOfKm = 0; tenthsOfKm <= 200; tenthsOfKm++) {
            BigDecimal fare = TIERS_ONLY.fareFor(BigDecimal.valueOf(tenthsOfKm, 1));
            assertThat(fare).isGreaterThanOrEqualTo(previous);
            previous = fare;
        }
    }

    @Test
    void rejectsNegativeDistance() {
        assertThatThrownBy(() -> TIERS_ONLY.fareFor(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMisconfiguredSlabs() {
        assertThatThrownBy(() -> new TieredRateCard(BigDecimal.ZERO,
                List.of(upTo("5", "8"), upTo("2", "10"), beyond("5"))))
                .hasMessageContaining("ascending");
        assertThatThrownBy(() -> new TieredRateCard(BigDecimal.ZERO, List.of(upTo("2", "10"))))
                .hasMessageContaining("unbounded");
        assertThatThrownBy(() -> new TieredRateCard(BigDecimal.ZERO,
                List.of(beyond("5"), upTo("2", "10"))))
                .hasMessageContaining("Only the last slab");
    }
}
