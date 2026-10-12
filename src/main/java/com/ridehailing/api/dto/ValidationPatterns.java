package com.ridehailing.api.dto;

final class ValidationPatterns {

    static final String PHONE = "\\+?[0-9]{10,15}";
    static final String COUPON_CODE = "[A-Za-z0-9_-]{1,32}";
    static final String COUPON_CODE_MESSAGE = "must be 1-32 letters, digits, '_' or '-'";

    private ValidationPatterns() {
    }
}
