package com.ridehailing.service;

import com.ridehailing.domain.Ride;

import java.util.List;

public record RideHistory(List<Ride> ongoing, List<Ride> completed, List<Ride> cancelled) {
}
