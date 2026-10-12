package com.ridehailing.pricing;

import com.ridehailing.domain.Cab;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.repository.DriverRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilizationSurgePricingTest {

    private static final Location PICKUP = new Location(0, 0);

    private final DriverRepository drivers = new DriverRepository();
    private final SurgePricing surge = new UtilizationSurgePricing(drivers, 5, List.of(
            new UtilizationSurgePricing.Tier(0.9, new BigDecimal("1.5")),
            new UtilizationSurgePricing.Tier(0.75, new BigDecimal("1.25"))));

    /** Places {@code total} cabs at the pickup, {@code busy} of them on a ride. */
    private List<Driver> cabsNearby(int total, int busy) {
        List<Driver> placed = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String id = drivers.nextId();
            Driver driver = new Driver(id, "d", "p", new Cab("plate-" + id, CarType.SEDAN, PICKUP), 4.5);
            drivers.saveIfPlateUnique(driver);
            if (i < busy) {
                driver.tryAssign();
            }
            placed.add(driver);
        }
        return placed;
    }

    @Test
    void noSurgeBelowLowestThreshold() {
        cabsNearby(4, 2);   // 50% busy

        assertThat(surge.multiplierAt(PICKUP)).isEqualByComparingTo("1");
    }

    @Test
    void appliesTierAtExactThreshold() {
        cabsNearby(4, 3);   // 75% busy

        assertThat(surge.multiplierAt(PICKUP)).isEqualByComparingTo("1.25");
    }

    @Test
    void appliesHighestTierMet() {
        cabsNearby(10, 9);  // 90% busy

        assertThat(surge.multiplierAt(PICKUP)).isEqualByComparingTo("1.5");
    }

    @Test
    void ignoresCabsOutsideTheRadius() {
        cabsNearby(4, 1);
        Driver farBusy = new Driver(drivers.nextId(), "d", "p", new Cab("far", CarType.SEDAN, new Location(50, 0)), 4.5);
        drivers.saveIfPlateUnique(farBusy);
        farBusy.tryAssign();

        assertThat(surge.multiplierAt(PICKUP)).isEqualByComparingTo("1");   // still 1 of 4 nearby
    }

    @Test
    void noSurgeWhenNoCabsNearby() {
        assertThat(surge.multiplierAt(PICKUP)).isEqualByComparingTo("1");
    }

    @Test
    void rejectsInvalidTiers() {
        assertThatThrownBy(() -> new UtilizationSurgePricing.Tier(1.2, BigDecimal.TWO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UtilizationSurgePricing.Tier(0.5, new BigDecimal("0.8")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
