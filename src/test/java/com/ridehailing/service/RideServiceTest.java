package com.ridehailing.service;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.DriverStatus;
import com.ridehailing.domain.Location;
import com.ridehailing.domain.Ride;
import com.ridehailing.domain.RideStatus;
import com.ridehailing.domain.User;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.pricing.PercentageDiscount;
import com.ridehailing.pricing.UtilizationSurgePricing;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.List;

import static com.ridehailing.domain.CarType.HATCHBACK;
import static com.ridehailing.domain.CarType.SEDAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RideServiceTest {

    private static final Location ORIGIN = new Location(0, 0);

    private final TestApp app = new TestApp();

    private static void assertFailsWith(ErrorCode expected, Executable action) {
        DomainException error = assertThrows(DomainException.class, action);
        assertThat(error.code()).isEqualTo(expected);
    }

    @Nested
    class Booking {

        @Test
        void assignsNearestAvailableDriverOfRequestedType() {
            User user = app.user();
            app.driver(HATCHBACK, 3, 0);
            Driver nearest = app.driver(HATCHBACK, 1, 0);

            Ride ride = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            assertThat(ride.driverId()).isEqualTo(nearest.id());
            assertThat(ride.status()).isEqualTo(RideStatus.ONGOING);
            assertThat(ride.upgraded()).isFalse();
            assertThat(nearest.status()).isEqualTo(DriverStatus.ON_RIDE);
        }

        @Test
        void includesDriverExactlyOnTheRadiusBoundary() {
            User user = app.user();
            Driver onBoundary = app.driver(HATCHBACK, 3, 4);     // exactly 5 km

            Ride ride = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            assertThat(ride.driverId()).isEqualTo(onBoundary.id());
        }

        @Test
        void failsWhenNoDriverWithinRadiusAndReleasesTheUser() {
            User user = app.user();
            app.driver(HATCHBACK, 5.01, 0);
            app.driver(SEDAN, 0, 6);

            assertFailsWith(ErrorCode.NO_DRIVER_AVAILABLE,
                    () -> app.rides.book(user.id(), ORIGIN, HATCHBACK, null));

            // the failed attempt must not leave the user stuck "on a ride"
            Driver nearby = app.driver(HATCHBACK, 1, 1);
            assertThat(app.rides.book(user.id(), ORIGIN, HATCHBACK, null).driverId()).isEqualTo(nearby.id());
        }

        @Test
        void skipsDriversAlreadyOnARide() {
            Driver busy = app.driver(HATCHBACK, 1, 0);
            Driver free = app.driver(HATCHBACK, 2, 0);
            app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);

            Ride second = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);

            assertThat(busy.status()).isEqualTo(DriverStatus.ON_RIDE);
            assertThat(second.driverId()).isEqualTo(free.id());
        }

        @Test
        void rejectsSecondBookingWhileUserHasAnOngoingRide() {
            User user = app.user();
            app.driver(HATCHBACK, 1, 0);
            Driver untouched = app.driver(HATCHBACK, 2, 0);
            app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            assertFailsWith(ErrorCode.USER_HAS_ONGOING_RIDE,
                    () -> app.rides.book(user.id(), ORIGIN, HATCHBACK, null));
            assertThat(untouched.isAvailable()).isTrue();
        }

        @Test
        void rejectsUnknownUser() {
            app.driver(HATCHBACK, 1, 0);

            assertFailsWith(ErrorCode.USER_NOT_FOUND,
                    () -> app.rides.book("user-999", ORIGIN, HATCHBACK, null));
        }
    }

    @Nested
    class FreeUpgrade {

        @Test
        void upgradesHatchbackToSedanAndBillsAtHatchbackRate() {
            User user = app.user();
            Driver sedan = app.driver(SEDAN, 0, 0);

            Ride ride = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);
            assertThat(ride.driverId()).isEqualTo(sedan.id());
            assertThat(ride.assignedCarType()).isEqualTo(SEDAN);
            assertThat(ride.upgraded()).isTrue();

            app.drivers.updateLocation(sedan.id(), new Location(6, 8));   // 10 km trip
            Ride ended = app.rides.end(ride.id());

            assertThat(ended.fare().total()).isEqualByComparingTo("69");   // hatchback rate; sedan would be 106
        }

        @Test
        void prefersRequestedTypeEvenIfASedanIsCloser() {
            User user = app.user();
            app.driver(SEDAN, 0.5, 0);
            Driver hatchback = app.driver(HATCHBACK, 4, 0);

            Ride ride = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            assertThat(ride.driverId()).isEqualTo(hatchback.id());
            assertThat(ride.upgraded()).isFalse();
        }

        @Test
        void neverDowngradesSedanToHatchback() {
            User user = app.user();
            app.driver(HATCHBACK, 1, 0);

            assertFailsWith(ErrorCode.NO_DRIVER_AVAILABLE,
                    () -> app.rides.book(user.id(), ORIGIN, SEDAN, null));
        }
    }

    @Nested
    class EndingARide {

        @Test
        void pricesFromPickupToCabLocationAndFreesDriverAndUser() {
            User user = app.user();
            Driver driver = app.driver(SEDAN, 1, 1);
            Ride ride = app.rides.book(user.id(), new Location(1, 1), SEDAN, null);

            app.drivers.updateLocation(driver.id(), new Location(4, 5));  // 5 km from pickup
            Ride ended = app.rides.end(ride.id());

            assertThat(ended.status()).isEqualTo(RideStatus.COMPLETED);
            assertThat(ended.fare().distanceKm()).isEqualByComparingTo("5");
            assertThat(ended.fare().total()).isEqualByComparingTo("80");   // sedan tiered 66, minimum 80
            assertThat(ended.dropoff()).isEqualTo(new Location(4, 5));
            assertThat(ended.endedAt()).isEqualTo(app.clock.instant());
            assertThat(driver.isAvailable()).isTrue();
            assertThat(app.rides.book(user.id(), new Location(4, 5), SEDAN, null).driverId()).isEqualTo(driver.id());
        }

        @Test
        void chargesMinimumFareWhenCabHasNotMoved() {
            app.driver(HATCHBACK, 0, 0);
            Ride ride = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);

            assertThat(app.rides.end(ride.id()).fare().total()).isEqualByComparingTo("50");
        }

        @Test
        void cannotEndARideTwice() {
            app.driver(HATCHBACK, 0, 0);
            Ride ride = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);
            app.rides.end(ride.id());

            assertFailsWith(ErrorCode.RIDE_NOT_ONGOING, () -> app.rides.end(ride.id()));
        }

        @Test
        void rejectsUnknownRide() {
            assertFailsWith(ErrorCode.RIDE_NOT_FOUND, () -> app.rides.end("ride-404"));
        }
    }

    @Nested
    class Coupons {

        private void addCoupon(String code, String percent) {
            app.coupons.add(code, new PercentageDiscount(new BigDecimal(percent), null));
        }

        @Test
        void appliesValidCouponGivenAtBooking() {
            addCoupon("SAVE10", "10");
            Driver driver = app.driver(HATCHBACK, 0, 0);
            Ride ride = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, "save10");   // case-insensitive

            app.drivers.updateLocation(driver.id(), new Location(0, 10));
            Ride ended = app.rides.end(ride.id());

            assertThat(ended.couponCode()).isEqualTo("SAVE10");
            assertThat(ended.fare().discount()).isEqualByComparingTo("6.90");
            assertThat(ended.fare().total()).isEqualByComparingTo("62.10");
        }

        @Test
        void rejectsInvalidCouponWithoutReservingAnything() {
            User user = app.user();
            Driver driver = app.driver(HATCHBACK, 0, 0);

            assertFailsWith(ErrorCode.INVALID_COUPON,
                    () -> app.rides.book(user.id(), ORIGIN, HATCHBACK, "NOPE"));

            assertThat(driver.isAvailable()).isTrue();
            assertThat(app.rides.book(user.id(), ORIGIN, HATCHBACK, null)).isNotNull();
        }

        @Test
        void rejectsDeletedCoupon() {
            addCoupon("GONE", "10");
            app.coupons.delete("GONE");
            app.driver(HATCHBACK, 0, 0);

            assertFailsWith(ErrorCode.INVALID_COUPON,
                    () -> app.rides.book(app.user().id(), ORIGIN, HATCHBACK, "GONE"));
        }

        @Test
        void couponDeletedAfterBookingStillApplies() {
            addCoupon("HALF", "50");
            app.driver(HATCHBACK, 0, 0);
            Ride ride = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, "HALF");

            app.coupons.delete("HALF");

            assertThat(app.rides.end(ride.id()).fare().total()).isEqualByComparingTo("25");
        }

        @Test
        void rejectsDuplicateCouponCodeAndDeletingUnknownCoupon() {
            addCoupon("SAVE10", "10");

            assertFailsWith(ErrorCode.DUPLICATE_COUPON, () -> addCoupon("save10", "20"));
            assertFailsWith(ErrorCode.COUPON_NOT_FOUND, () -> app.coupons.delete("MISSING"));
        }
    }

    @Nested
    class Surge {

        private final TestApp surgeApp = new TestApp(drivers -> new UtilizationSurgePricing(drivers, TestApp.RADIUS_KM,
                List.of(new UtilizationSurgePricing.Tier(0.75, new BigDecimal("1.25")))));

        @Test
        void multiplierIsFixedAtBookingAndAppliedWhenTheRideEnds() {
            for (int i = 0; i < 4; i++) {
                surgeApp.driver(HATCHBACK, 0, 0);
            }
            for (int i = 0; i < 3; i++) {
                surgeApp.rides.book(surgeApp.user().id(), ORIGIN, HATCHBACK, null);   // 3 of 4 cabs busy
            }

            Ride surged = surgeApp.rides.book(surgeApp.user().id(), ORIGIN, HATCHBACK, null);
            assertThat(surged.surgeMultiplier()).isEqualByComparingTo("1.25");

            surgeApp.drivers.updateLocation(surged.driverId(), new Location(6, 8));   // 10 km
            assertThat(surgeApp.rides.end(surged.id()).fare().total()).isEqualByComparingTo("86.25");  // 69 * 1.25
        }
    }

    @Nested
    class Cancellation {

        @Test
        void isFreeUpToAndIncludingTheGracePeriodAndFreesDriverAndUser() {
            User user = app.user();
            Driver driver = app.driver(HATCHBACK, 0, 0);
            Ride ride = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            app.clock.advance(TestApp.GRACE_PERIOD);
            Ride cancelled = app.rides.cancel(ride.id());

            assertThat(cancelled.status()).isEqualTo(RideStatus.CANCELLED);
            assertThat(cancelled.cancellationFee()).isEqualByComparingTo("0");
            assertThat(cancelled.fare()).isNull();
            assertThat(driver.isAvailable()).isTrue();
            assertThat(app.rides.book(user.id(), ORIGIN, HATCHBACK, null)).isNotNull();
        }

        @Test
        void chargesTheBilledCarTypesFeeAfterTheGracePeriod() {
            app.driver(SEDAN, 0, 0);
            Ride upgraded = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);

            app.clock.advance(TestApp.GRACE_PERIOD.plusSeconds(1));

            assertThat(app.rides.cancel(upgraded.id()).cancellationFee()).isEqualByComparingTo("25");  // not sedan's 40
        }

        @Test
        void cannotCancelAnEndedRideOrEndACancelledOne() {
            app.driver(HATCHBACK, 0, 0);
            Ride ended = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);
            app.rides.end(ended.id());
            Ride cancelled = app.rides.book(app.user().id(), ORIGIN, HATCHBACK, null);
            app.rides.cancel(cancelled.id());

            assertFailsWith(ErrorCode.RIDE_NOT_ONGOING, () -> app.rides.cancel(ended.id()));
            assertFailsWith(ErrorCode.RIDE_NOT_ONGOING, () -> app.rides.end(cancelled.id()));
        }
    }

    @Nested
    class History {

        @Test
        void separatesOngoingAndCompletedRidesForUserAndDriver() {
            User user = app.user();
            Driver first = app.driver(HATCHBACK, 0, 0);
            Ride completed = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);
            app.rides.end(completed.id());
            first.cab().moveTo(new Location(50, 50));    // out of range for the next booking
            Driver second = app.driver(HATCHBACK, 0, 0);
            Ride ongoing = app.rides.book(user.id(), ORIGIN, HATCHBACK, null);

            RideHistory userHistory = app.rides.historyForUser(user.id());
            assertThat(userHistory.ongoing()).extracting(Ride::id).containsExactly(ongoing.id());
            assertThat(userHistory.completed()).extracting(Ride::id).containsExactly(completed.id());

            assertThat(app.rides.historyForDriver(first.id()).completed()).extracting(Ride::id)
                    .containsExactly(completed.id());
            assertThat(app.rides.historyForDriver(second.id()).ongoing()).extracting(Ride::id)
                    .containsExactly(ongoing.id());
        }

        @Test
        void rejectsHistoryForUnknownUserOrDriver() {
            assertFailsWith(ErrorCode.USER_NOT_FOUND, () -> app.rides.historyForUser("user-404"));
            assertFailsWith(ErrorCode.DRIVER_NOT_FOUND, () -> app.rides.historyForDriver("driver-404"));
        }
    }

    @Test
    void rejectsDuplicateRegistrations() {
        User user = app.user();
        Driver driver = app.driver(CarType.SEDAN, 0, 0);

        assertFailsWith(ErrorCode.DUPLICATE_USER, () -> app.users.register("Copy", user.phone()));
        assertFailsWith(ErrorCode.DUPLICATE_CAB, () -> app.drivers.register("Copy", "7000000000",
                driver.cab().plateNumber().toLowerCase(), CarType.SEDAN, ORIGIN, 4.0));
    }
}
