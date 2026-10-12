package com.ridehailing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateLocationRequest(@NotNull @Valid LocationDto location) {
}
