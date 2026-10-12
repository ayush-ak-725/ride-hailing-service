package com.ridehailing.service;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Location;
import com.ridehailing.domain.Ride;
import com.ridehailing.domain.User;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConcurrencyTest {

    private static final int RIDERS = 32;
    private static final Location PICKUP = new Location(0, 0);

    /** Every rider starts at the same instant and targets the same nearest driver. */
    @RepeatedTest(20)
    void eachDriverIsAssignedToAtMostOneRideUnderContention() throws Exception {
        TestApp app = new TestApp();
        app.driver(CarType.HATCHBACK, 0, 0);
        app.driver(CarType.HATCHBACK, 1, 0);
        List<User> riders = IntStream.range(0, RIDERS).mapToObj(i -> app.user()).toList();

        List<Object> outcomes = bookAllAtOnce(riders, rider -> app.rides.book(rider.id(), PICKUP, CarType.HATCHBACK, null));

        List<Ride> booked = outcomes.stream().filter(Ride.class::isInstance).map(Ride.class::cast).toList();
        assertThat(booked).hasSize(2);
        assertThat(booked).extracting(Ride::driverId).doesNotHaveDuplicates();
        assertThat(outcomes).filteredOn(DomainException.class::isInstance)
                .extracting(e -> ((DomainException) e).code())
                .hasSize(RIDERS - 2)
                .containsOnly(ErrorCode.NO_DRIVER_AVAILABLE);
    }

    private interface Booking {
        Ride book(User rider);
    }

    /** Returns each rider's outcome: the booked Ride, or the DomainException it failed with. */
    private static List<Object> bookAllAtOnce(List<User> riders, Booking booking) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(riders.size())) {
            List<Future<Object>> futures = new ArrayList<>();
            for (User rider : riders) {
                Callable<Object> task = () -> {
                    start.await();
                    try {
                        return booking.book(rider);
                    } catch (DomainException e) {
                        return e;
                    }
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }
}
