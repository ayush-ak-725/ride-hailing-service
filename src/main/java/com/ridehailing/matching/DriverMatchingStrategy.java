package com.ridehailing.matching;

import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;

import java.util.List;

/**
 * Orders eligible drivers by preference. Booking tries them in order, so a driver lost to a
 * concurrent booking falls through to the next-best one.
 */
public interface DriverMatchingStrategy {

    List<Driver> rank(List<Driver> candidates, Location pickup);
}
