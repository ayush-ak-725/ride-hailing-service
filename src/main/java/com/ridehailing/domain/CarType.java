package com.ridehailing.domain;

import java.util.Optional;

public enum CarType {
    HATCHBACK,
    SEDAN;

    /**
     * The car type offered at no extra cost when this type is unavailable.
     * The rider is still billed at the rate of the type they requested.
     */
    public Optional<CarType> freeUpgrade() {
        return switch (this) {
            case HATCHBACK -> Optional.of(SEDAN);
            case SEDAN -> Optional.empty();
        };
    }
}
