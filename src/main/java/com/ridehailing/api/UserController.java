package com.ridehailing.api;

import com.ridehailing.api.dto.RegisterUserRequest;
import com.ridehailing.api.dto.RideHistoryResponse;
import com.ridehailing.api.dto.UserResponse;
import com.ridehailing.service.RideService;
import com.ridehailing.service.UserService;
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

@Tag(name = "Users")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final RideService rideService;

    public UserController(UserService userService, RideService rideService) {
        this.userService = userService;
        this.rideService = rideService;
    }

    @Operation(summary = "Register a rider", description = "409 DUPLICATE_USER if the phone number is taken.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(userService.register(request.name(), request.phone()));
    }

    @Operation(summary = "Ride history for a rider", description = "Ongoing, completed and cancelled rides, newest first.")
    @GetMapping("/{userId}/rides")
    public RideHistoryResponse rides(@PathVariable String userId) {
        return RideHistoryResponse.from(rideService.historyForUser(userId));
    }
}
