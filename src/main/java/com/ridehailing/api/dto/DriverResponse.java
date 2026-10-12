package com.ridehailing.api.dto;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.DriverStatus;

public record DriverResponse(String id, String name, String phone, double rating, DriverStatus status, CabResponse cab) {

    public record CabResponse(String plateNumber, CarType carType, LocationDto location) {
    }

    public static DriverResponse from(Driver driver) {
        var cab = driver.cab();
        return new DriverResponse(driver.id(), driver.name(), driver.phone(), driver.rating(), driver.status(),
                new CabResponse(cab.plateNumber(), cab.carType(), LocationDto.from(cab.location())));
    }
}
