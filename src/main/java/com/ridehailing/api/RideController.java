package com.ridehailing.api;

import com.ridehailing.api.dto.BookRideRequest;
import com.ridehailing.api.dto.RideResponse;
import com.ridehailing.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rides")
@RestController
@RequestMapping("/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @Operation(summary = "Book and start a ride",
            description = """
                    Assigns an available driver of the requested car type within the search radius. \
                    If no HATCHBACK is available, a SEDAN is assigned at the HATCHBACK price (upgraded=true). \
                    Errors: 404 USER_NOT_FOUND, 409 NO_DRIVER_AVAILABLE, 409 USER_HAS_ONGOING_RIDE, 422 INVALID_COUPON.""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RideResponse book(@Valid @RequestBody BookRideRequest request) {
        return RideResponse.from(rideService.book(request.userId(), request.pickup().toLocation(),
                request.carType(), request.couponCode()));
    }

    @Operation(summary = "End a ride and get its fare",
            description = "Drop-off is the cab's current location. Errors: 404 RIDE_NOT_FOUND, 409 RIDE_NOT_ONGOING.")
    @PostMapping("/{rideId}/end")
    public RideResponse end(@PathVariable String rideId) {
        return RideResponse.from(rideService.end(rideId));
    }

    @Operation(summary = "Cancel an ongoing ride",
            description = "Free within the grace period after booking, then a flat fee per car type. "
                    + "Errors: 404 RIDE_NOT_FOUND, 409 RIDE_NOT_ONGOING.")
    @PostMapping("/{rideId}/cancel")
    public RideResponse cancel(@PathVariable String rideId) {
        return RideResponse.from(rideService.cancel(rideId));
    }

    @Operation(summary = "Get a ride")
    @GetMapping("/{rideId}")
    public RideResponse get(@PathVariable String rideId) {
        return RideResponse.from(rideService.get(rideId));
    }
}
