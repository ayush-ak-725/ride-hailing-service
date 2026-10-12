package com.ridehailing.api;

import com.ridehailing.api.dto.CouponResponse;
import com.ridehailing.api.dto.CreateCouponRequest;
import com.ridehailing.pricing.PercentageDiscount;
import com.ridehailing.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Coupons")
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "Add a percentage coupon",
            description = "Codes are case-insensitive and stored upper-case. 409 DUPLICATE_COUPON if the code exists.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse add(@Valid @RequestBody CreateCouponRequest request) {
        PercentageDiscount discount = request.toDiscount();
        return CouponResponse.from(couponService.add(request.code(), discount), discount);
    }

    @Operation(summary = "Delete a coupon",
            description = "Rides already booked with it keep their discount. 404 COUPON_NOT_FOUND if unknown.")
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        couponService.delete(code);
    }
}
