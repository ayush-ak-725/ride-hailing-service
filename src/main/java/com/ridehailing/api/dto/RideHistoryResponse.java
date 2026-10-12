package com.ridehailing.api.dto;

import com.ridehailing.domain.Ride;
import com.ridehailing.service.RideHistory;

import java.util.List;

public record RideHistoryResponse(List<RideResponse> ongoing, List<RideResponse> completed, List<RideResponse> cancelled) {

    public static RideHistoryResponse from(RideHistory history) {
        return new RideHistoryResponse(map(history.ongoing()), map(history.completed()), map(history.cancelled()));
    }

    private static List<RideResponse> map(List<Ride> rides) {
        return rides.stream().map(RideResponse::from).toList();
    }
}
