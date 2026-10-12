package com.ridehailing.repository;

import com.ridehailing.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class UserRepository {

    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByPhone = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String nextId() {
        return "user-" + sequence.incrementAndGet();
    }

    /** Saves the user unless the phone number is taken. Atomic, so concurrent duplicates are safe. */
    public boolean saveIfPhoneUnique(User user) {
        if (userIdByPhone.putIfAbsent(user.phone(), user.id()) != null) {
            return false;
        }
        usersById.put(user.id(), user);
        return true;
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }
}
