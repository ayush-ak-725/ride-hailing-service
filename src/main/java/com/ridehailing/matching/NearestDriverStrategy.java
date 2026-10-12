package com.ridehailing.matching;

import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;

import java.util.Comparator;
import java.util.List;

public class NearestDriverStrategy implements DriverMatchingStrategy {

    @Override
    public List<Driver> rank(List<Driver> candidates, Location pickup) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(driver -> driver.cab().location().distanceTo(pickup)))
                .toList();
    }
}
