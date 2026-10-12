package com.ridehailing.error;

/** An expected business-rule failure, e.g. no driver in radius or an invalid coupon. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
