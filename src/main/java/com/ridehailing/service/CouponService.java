package com.ridehailing.service;

import com.ridehailing.domain.Coupon;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.pricing.Discount;
import com.ridehailing.repository.CouponRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CouponService {

    private final CouponRepository coupons;

    public CouponService(CouponRepository coupons) {
        this.coupons = coupons;
    }

    public Coupon add(String code, Discount discount) {
        Coupon coupon = new Coupon(normalize(code), discount);
        if (!coupons.saveIfAbsent(coupon)) {
            throw new DomainException(ErrorCode.DUPLICATE_COUPON, "Coupon " + coupon.code() + " already exists");
        }
        return coupon;
    }

    public void delete(String code) {
        if (!coupons.delete(normalize(code))) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "Coupon " + normalize(code) + " not found");
        }
    }

    /** Looks up a coupon being applied to a booking; unknown or deleted codes are rejected. */
    public Coupon resolveForBooking(String code) {
        return coupons.findByCode(normalize(code))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_COUPON, "Coupon " + normalize(code) + " is not valid"));
    }

    /** Codes are case-insensitive: "save10" and "SAVE10" are the same coupon. */
    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
