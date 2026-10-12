package com.ridehailing.api;

import com.ridehailing.api.dto.DriverResponse;
import com.ridehailing.api.dto.RegisterDriverRequest;
import com.ridehailing.api.dto.RideHistoryResponse;
import com.ridehailing.api.dto.UpdateLocationRequest;
import com.ridehailing.service.DriverService;
import com.ridehailing.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drivers")
@RestController
@RequestMapping("/drivers")
public class DriverController {

    private final DriverService driverService;
    private final RideService rideService;

    public DriverController(DriverService driverService, RideService rideService) {
        this.driverService = driverService;
        this.rideService = rideService;
    }

    @Operation(summary = "Register a driver with their cab", description = "409 DUPLICATE_CAB if the plate is already registered.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponse register(@Valid @RequestBody RegisterDriverRequest request) {
        return DriverResponse.from(driverService.register(request.name(), request.phone(), request.plateNumber(),
                request.carType(), request.location().toLocation(), request.ratingOrDefault()));
    }

    @Operation(summary = "Update the cab's current location",
            description = "Allowed during a ride; the cab's location when the ride ends is the drop-off point.")
    @PutMapping("/{driverId}/location")
    public DriverResponse updateLocation(@PathVariable String driverId, @Valid @RequestBody UpdateLocationRequest request) {
        return DriverResponse.from(driverService.updateLocation(driverId, request.location().toLocation()));
    }

    @Operation(summary = "Ride history for a driver", description = "Ongoing, completed and cancelled rides, newest first.")
    @GetMapping("/{driverId}/rides")
    public RideHistoryResponse rides(@PathVariable String driverId) {
        return RideHistoryResponse.from(rideService.historyForDriver(driverId));
    }
}
