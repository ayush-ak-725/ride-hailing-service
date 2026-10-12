package com.ridehailing.domain;

import java.util.concurrent.atomic.AtomicReference;

public class Driver {

    private final String id;
    private final String name;
    private final String phone;
    private final Cab cab;
    private final double rating;
    private final AtomicReference<DriverStatus> status = new AtomicReference<>(DriverStatus.AVAILABLE);

    public Driver(String id, String name, String phone, Cab cab, double rating) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.cab = cab;
        this.rating = rating;
    }

    /**
     * Atomically claims this driver for a ride. Exactly one of any number of concurrent callers
     * wins; the rest get false and should try another driver. In a database this would be
     * {@code UPDATE driver SET status='ON_RIDE' WHERE id=? AND status='AVAILABLE'}.
     */
    public boolean tryAssign() {
        return status.compareAndSet(DriverStatus.AVAILABLE, DriverStatus.ON_RIDE);
    }

    public void release() {
        status.set(DriverStatus.AVAILABLE);
    }

    public boolean isAvailable() {
        return status.get() == DriverStatus.AVAILABLE;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String phone() {
        return phone;
    }

    public Cab cab() {
        return cab;
    }

    public double rating() {
        return rating;
    }

    public DriverStatus status() {
        return status.get();
    }
}
