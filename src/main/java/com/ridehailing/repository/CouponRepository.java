package com.ridehailing.repository;

import com.ridehailing.domain.Coupon;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CouponRepository {

    private final Map<String, Coupon> couponsByCode = new ConcurrentHashMap<>();

    public boolean saveIfAbsent(Coupon coupon) {
        return couponsByCode.putIfAbsent(coupon.code(), coupon) == null;
    }

    public Optional<Coupon> findByCode(String code) {
        return Optional.ofNullable(couponsByCode.get(code));
    }

    public boolean delete(String code) {
        return couponsByCode.remove(code) != null;
    }
}
