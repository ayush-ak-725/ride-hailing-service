package com.ridehailing.api.dto;

import com.ridehailing.domain.User;

public record UserResponse(String id, String name, String phone) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.phone());
    }
}
