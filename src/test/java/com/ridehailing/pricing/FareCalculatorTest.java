package com.ridehailing.pricing;

import com.ridehailing.domain.CarType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareCalculatorTest {

    private static final BigDecimal NO_SURGE = BigDecimal.ONE;

    private final FareCalculator calculator = PricingFixtures.fareCalculator();

    private static BigDecimal km(String value) {
        return new BigDecimal(value);
    }

    @Test
    void appliesMinimumFareToShortRide() {
        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("1"), NO_SURGE, null);

        assertThat(fare.baseFare()).isEqualByComparingTo("50");
        assertThat(fare.total()).isEqualByComparingTo("50");
    }

    @Test
    void chargesDifferentRatesPerCarType() {
        FareBreakdown hatchback = calculator.calculate(CarType.HATCHBACK, km("10"), NO_SURGE, null);
        FareBreakdown sedan = calculator.calculate(CarType.SEDAN, km("10"), NO_SURGE, null);

        assertThat(hatchback.total()).isEqualByComparingTo("69");   // 20 + 24 + 25
        assertThat(sedan.total()).isEqualByComparingTo("106");      // 30 + 36 + 40
    }

    @Test
    void appliesEachCarTypesOwnMinimumFare() {
        FareBreakdown sedan = calculator.calculate(CarType.SEDAN, km("3"), NO_SURGE, null);

        assertThat(sedan.total()).isEqualByComparingTo("80");       // tiered 42, sedan minimum 80
    }

    @Test
    void appliesPercentageCoupon() {
        Discount tenPercent = new PercentageDiscount(new BigDecimal("10"), null);

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("10"), NO_SURGE, tenPercent);

        assertThat(fare.discount()).isEqualByComparingTo("6.90");
        assertThat(fare.total()).isEqualByComparingTo("62.10");
    }

    @Test
    void capsCouponDiscountAtItsMaximum() {
        Discount halfOffUpTo20 = new PercentageDiscount(new BigDecimal("50"), new BigDecimal("20"));

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("10"), NO_SURGE, halfOffUpTo20);

        assertThat(fare.discount()).isEqualByComparingTo("20");
        assertThat(fare.total()).isEqualByComparingTo("49");
    }

    @Test
    void couponAppliesAfterMinimumFare() {
        Discount halfOff = new PercentageDiscount(new BigDecimal("50"), null);

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("1"), NO_SURGE, halfOff);

        assertThat(fare.baseFare()).isEqualByComparingTo("50");
        assertThat(fare.total()).isEqualByComparingTo("25");
    }

    @Test
    void discountNeverMakesFareNegative() {
        Discount moreThanTheFare = fare -> fare.add(new BigDecimal("100"));

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("10"), NO_SURGE, moreThanTheFare);

        assertThat(fare.discount()).isEqualByComparingTo("69");
        assertThat(fare.total()).isEqualByComparingTo("0");
    }

    @Test
    void appliesSurgeBeforeCoupon() {
        Discount tenPercent = new PercentageDiscount(new BigDecimal("10"), null);

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("10"), new BigDecimal("1.5"), tenPercent);

        assertThat(fare.surgedFare()).isEqualByComparingTo("103.50");
        assertThat(fare.discount()).isEqualByComparingTo("10.35");
        assertThat(fare.total()).isEqualByComparingTo("93.15");
    }

    @Test
    void pricesTheRoundedDistanceAndBreakdownAddsUp() {
        Discount thirdOff = new PercentageDiscount(new BigDecimal("33.333"), null);

        FareBreakdown fare = calculator.calculate(CarType.HATCHBACK, km("7.333"), NO_SURGE, thirdOff);

        assertThat(fare.distanceKm()).isEqualByComparingTo("7.33");
        assertThat(fare.baseFare()).isEqualByComparingTo("55.65");  // priced on the shown 7.33 km: 44 + 2.33*5
        assertThat(fare.discount()).isEqualByComparingTo("18.55");  // 55.65 * 33.333% = 18.5498 -> 18.55
        assertThat(fare.baseFare().scale()).isEqualTo(2);
        assertThat(fare.surgedFare().subtract(fare.discount())).isEqualByComparingTo(fare.total());
    }

    @Test
    void rejectsSurgeBelowOne() {
        assertThatThrownBy(() -> calculator.calculate(CarType.HATCHBACK, km("1"), new BigDecimal("0.9"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresARateCardForEveryCarType() {
        assertThatThrownBy(() -> new FareCalculator(Map.of(CarType.HATCHBACK, PricingFixtures.HATCHBACK)))
                .hasMessageContaining("SEDAN");
    }
}
