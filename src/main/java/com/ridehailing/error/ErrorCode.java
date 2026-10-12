package com.ridehailing.error;

/** Business error codes returned to clients. The HTTP mapping lives in the API layer. */
public enum ErrorCode {
    USER_NOT_FOUND,
    DRIVER_NOT_FOUND,
    RIDE_NOT_FOUND,
    COUPON_NOT_FOUND,
    DUPLICATE_USER,
    DUPLICATE_CAB,
    DUPLICATE_COUPON,
    NO_DRIVER_AVAILABLE,
    USER_HAS_ONGOING_RIDE,
    RIDE_NOT_ONGOING,
    INVALID_COUPON
}
