package com.ridehailing.repository;

import com.ridehailing.domain.Ride;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Repository
public class RideRepository {

    private static final Comparator<Ride> NEWEST_FIRST = Comparator.comparing(Ride::startedAt).reversed();

    private final Map<String, Ride> ridesById = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String nextId() {
        return "ride-" + sequence.incrementAndGet();
    }

    public void save(Ride ride) {
        ridesById.put(ride.id(), ride);
    }

    public Optional<Ride> findById(String id) {
        return Optional.ofNullable(ridesById.get(id));
    }

    public List<Ride> findByUserId(String userId) {
        return findWhere(ride -> ride.userId().equals(userId));
    }

    public List<Ride> findByDriverId(String driverId) {
        return findWhere(ride -> ride.driverId().equals(driverId));
    }

    // Full scan; a database would index rides on user_id and driver_id.
    private List<Ride> findWhere(Predicate<Ride> predicate) {
        return ridesById.values().stream().filter(predicate).sorted(NEWEST_FIRST).toList();
    }
}
