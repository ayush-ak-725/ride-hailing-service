package com.ridehailing.service;

import com.ridehailing.domain.Cab;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.repository.DriverRepository;
import org.springframework.stereotype.Service;

@Service
public class DriverService {

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    public Driver register(String name, String phone, String plateNumber, CarType carType,
                           Location location, double rating) {
        Driver driver = new Driver(drivers.nextId(), name, phone, new Cab(plateNumber, carType, location), rating);
        if (!drivers.saveIfPlateUnique(driver)) {
            throw new DomainException(ErrorCode.DUPLICATE_CAB, "A cab with plate " + plateNumber + " is already registered");
        }
        return driver;
    }

    public Driver updateLocation(String driverId, Location location) {
        Driver driver = get(driverId);
        driver.cab().moveTo(location);
        return driver;
    }

    public Driver get(String driverId) {
        return drivers.findById(driverId)
                .orElseThrow(() -> new DomainException(ErrorCode.DRIVER_NOT_FOUND, "Driver " + driverId + " not found"));
    }
}
