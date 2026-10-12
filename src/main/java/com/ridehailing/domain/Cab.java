package com.ridehailing.domain;

import java.util.Objects;

public class Cab {

    private final String plateNumber;
    private final CarType carType;
    private volatile Location location;

    public Cab(String plateNumber, CarType carType, Location location) {
        this.plateNumber = plateNumber;
        this.carType = Objects.requireNonNull(carType, "carType");
        this.location = Objects.requireNonNull(location, "location");
    }

    public void moveTo(Location newLocation) {
        this.location = Objects.requireNonNull(newLocation, "location");
    }

    public String plateNumber() {
        return plateNumber;
    }

    public CarType carType() {
        return carType;
    }

    public Location location() {
        return location;
    }
}
