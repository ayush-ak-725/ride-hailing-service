package com.ridehailing.matching;

import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;

import java.util.Comparator;
import java.util.List;

/** Highest rating first; equally rated drivers are ordered by distance. */
public class HighestRatedDriverStrategy implements DriverMatchingStrategy {

    @Override
    public List<Driver> rank(List<Driver> candidates, Location pickup) {
        Comparator<Driver> byRatingDesc = Comparator.comparingDouble(Driver::rating).reversed();
        Comparator<Driver> byDistance = Comparator.comparingDouble(driver -> driver.cab().location().distanceTo(pickup));
        return candidates.stream().sorted(byRatingDesc.thenComparing(byDistance)).toList();
    }
}
