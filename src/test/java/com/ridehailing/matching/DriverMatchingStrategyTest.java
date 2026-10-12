package com.ridehailing.matching;

import com.ridehailing.domain.Cab;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriverMatchingStrategyTest {

    private static final Location PICKUP = new Location(0, 0);

    private static Driver driver(String id, double x, double rating) {
        return new Driver(id, id, "phone-" + id, new Cab("plate-" + id, CarType.SEDAN, new Location(x, 0)), rating);
    }

    private final Driver nearOk = driver("near-ok", 1, 4.0);
    private final Driver farBest = driver("far-best", 4, 4.9);
    private final Driver midBest = driver("mid-best", 2, 4.9);

    @Test
    void nearestStrategyOrdersByDistance() {
        List<Driver> ranked = new NearestDriverStrategy().rank(List.of(farBest, nearOk, midBest), PICKUP);

        assertThat(ranked).extracting(Driver::id).containsExactly("near-ok", "mid-best", "far-best");
    }

    @Test
    void highestRatedStrategyOrdersByRatingThenDistance() {
        List<Driver> ranked = new HighestRatedDriverStrategy().rank(List.of(farBest, nearOk, midBest), PICKUP);

        assertThat(ranked).extracting(Driver::id).containsExactly("mid-best", "far-best", "near-ok");
    }
}
