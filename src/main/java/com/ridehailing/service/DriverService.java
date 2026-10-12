package com.ridehailing.service;

import com.ridehailing.domain.Cab;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    /**
     * Drivers are unique by cab plate, not by phone: a fleet operator may register several cabs
     * under one contact number. Real driver identity would come from licence / KYC checks.
     */
    public Driver register(String name, String phone, String plateNumber, CarType carType,
                           Location location, double rating) {
        String plate = plateNumber.trim().toUpperCase(Locale.ROOT);   // "ka01ab1234" and "KA01AB1234" are one cab
        Driver driver = new Driver(drivers.nextId(), name, phone, new Cab(plate, carType, location), rating);
        if (!drivers.saveIfPlateUnique(driver)) {
            throw new DomainException(ErrorCode.DUPLICATE_CAB, "A cab with plate " + plate + " is already registered");
        }
        log.info("Driver registered: driverId={} carType={} plate={} location={}",
                driver.id(), carType, plate, location);
        return driver;
    }

    public Driver updateLocation(String driverId, Location location) {
        Driver driver = get(driverId);
        driver.cab().moveTo(location);
        log.debug("Cab location updated: driverId={} location={} status={}", driverId, location, driver.status());
        return driver;
    }

    public Driver get(String driverId) {
        return drivers.findById(driverId)
                .orElseThrow(() -> new DomainException(ErrorCode.DRIVER_NOT_FOUND, "Driver " + driverId + " not found"));
    }
}
