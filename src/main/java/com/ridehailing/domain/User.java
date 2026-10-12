package com.ridehailing.domain;

import java.util.concurrent.atomic.AtomicBoolean;

public class User {

    private final String id;
    private final String name;
    private final String phone;
    private final AtomicBoolean onRide = new AtomicBoolean(false);

    public User(String id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    /** Atomically reserves the user for a new ride; false if they already have one. */
    public boolean tryStartRide() {
        return onRide.compareAndSet(false, true);
    }

    public void finishRide() {
        onRide.set(false);
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
}
