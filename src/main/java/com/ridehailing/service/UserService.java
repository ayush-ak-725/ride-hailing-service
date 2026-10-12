package com.ridehailing.service;

import com.ridehailing.domain.User;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User register(String name, String phone) {
        User user = new User(users.nextId(), name, phone);
        if (!users.saveIfPhoneUnique(user)) {
            throw new DomainException(ErrorCode.DUPLICATE_USER, "A user with this phone number already exists");
        }
        log.info("User registered: userId={}", user.id());   // phone is personal data: not logged
        return user;
    }

    public User get(String userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND, "User " + userId + " not found"));
    }
}
