package com.ridehailing.repository;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class DriverRepository {

    private final Map<String, Driver> driversById = new ConcurrentHashMap<>();
    private final Map<String, String> driverIdByPlate = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String nextId() {
        return "driver-" + sequence.incrementAndGet();
    }

    /** Saves the driver unless the cab's plate is already registered. */
    public boolean saveIfPlateUnique(Driver driver) {
        if (driverIdByPlate.putIfAbsent(driver.cab().plateNumber(), driver.id()) != null) {
            return false;
        }
        driversById.put(driver.id(), driver);
        return true;
    }

    public Optional<Driver> findById(String id) {
        return Optional.ofNullable(driversById.get(id));
    }

    /** Every driver within the radius regardless of status; used to measure local utilization. */
    public List<Driver> findAllWithin(Location centre, double radiusKm) {
        return driversById.values().stream()
                .filter(driver -> driver.cab().location().distanceTo(centre) <= radiusKm)
                .toList();
    }

    /**
     * Linear scan over all drivers: fine for an in-memory demo. At city scale this becomes a
     * spatial index (geohash / grid cells, or PostGIS / Redis GEO) keyed by car type.
     * The result is a snapshot: a returned driver may be claimed by someone else a moment later,
     * which callers handle via {@link Driver#tryAssign()}.
     */
    public List<Driver> findAvailableWithin(CarType carType, Location centre, double radiusKm) {
        return driversById.values().stream()
                .filter(Driver::isAvailable)
                .filter(driver -> driver.cab().carType() == carType)
                .filter(driver -> driver.cab().location().distanceTo(centre) <= radiusKm)
                .toList();
    }
}
