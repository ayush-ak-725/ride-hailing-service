package com.ridehailing.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests against the full application context. The context (and its in-memory
 * state) is shared across tests, so each test works in its own far-away grid region and uses
 * unique phone numbers and plates.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RideHailingApiTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static final AtomicInteger REGION = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Test
    void completeRideLifecycleWithCoupon() throws Exception {
        double base = region();
        String userId = registerUser();
        String driverId = registerDriver("HATCHBACK", base, 0);
        String code = "LIFE" + UNIQUE.incrementAndGet();
        send(post("/coupons"), """
                {"code": "%s", "percentOff": 10}""".formatted(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));

        String rideId = id(send(post("/rides"), bookingJson(userId, base, 0, "HATCHBACK", code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.driverId").value(driverId))
                .andExpect(jsonPath("$.upgraded").value(false))
                .andExpect(jsonPath("$.fare").doesNotExist()));

        send(put("/drivers/" + driverId + "/location"), locationJson(base + 6, 8)).andExpect(status().isOk());

        send(post("/rides/" + rideId + "/end"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fare.distanceKm").value(10.0))
                .andExpect(jsonPath("$.fare.baseFare").value(69.0))
                .andExpect(jsonPath("$.fare.discount").value(6.9))
                .andExpect(jsonPath("$.fare.total").value(62.1));

        mvc.perform(get("/users/" + userId + "/rides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ongoing", hasSize(0)))
                .andExpect(jsonPath("$.completed[0].id").value(rideId));
        mvc.perform(get("/drivers/" + driverId + "/rides"))
                .andExpect(jsonPath("$.completed[0].id").value(rideId));
    }

    @Test
    void hatchbackRequestIsUpgradedToSedan() throws Exception {
        double base = region();
        String userId = registerUser();
        registerDriver("SEDAN", base, 1);

        send(post("/rides"), bookingJson(userId, base, 0, "HATCHBACK", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedCarType").value("HATCHBACK"))
                .andExpect(jsonPath("$.assignedCarType").value("SEDAN"))
                .andExpect(jsonPath("$.upgraded").value(true));
    }

    @Test
    void noDriverWithinRadiusReturns409() throws Exception {
        double base = region();
        String userId = registerUser();
        registerDriver("HATCHBACK", base + 6, 0);

        send(post("/rides"), bookingJson(userId, base, 0, "HATCHBACK", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_DRIVER_AVAILABLE"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.details", hasSize(0)));
    }

    @Test
    void invalidCouponReturns422() throws Exception {
        double base = region();
        String userId = registerUser();
        registerDriver("HATCHBACK", base, 0);

        send(post("/rides"), bookingJson(userId, base, 0, "HATCHBACK", "DOESNOTEXIST"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_COUPON"));
    }

    @Test
    void cancellingWithinGracePeriodIsFreeAndCannotBeRepeated() throws Exception {
        double base = region();
        String userId = registerUser();
        registerDriver("SEDAN", base, 0);
        String rideId = id(send(post("/rides"), bookingJson(userId, base, 0, "SEDAN", null)));

        send(post("/rides/" + rideId + "/cancel"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationFee").value(0.0));
        send(post("/rides/" + rideId + "/cancel"), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RIDE_NOT_ONGOING"));
        mvc.perform(get("/users/" + userId + "/rides"))
                .andExpect(jsonPath("$.cancelled[0].id").value(rideId));
    }

    @Test
    void deletingACouponTwiceReturns404() throws Exception {
        String code = "DEL" + UNIQUE.incrementAndGet();
        send(post("/coupons"), """
                {"code": "%s", "percentOff": 20, "maxDiscount": 30}""".formatted(code))
                .andExpect(jsonPath("$.maxDiscount").value(30));

        mvc.perform(delete("/coupons/" + code.toLowerCase())).andExpect(status().isNoContent());
        mvc.perform(delete("/coupons/" + code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    void invalidFieldsReturn400WithDetails() throws Exception {
        send(post("/drivers"), """
                {"name": "", "phone": "12", "plateNumber": "KA-1", "carType": "SEDAN",
                 "location": {"x": 1}, "rating": 7}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details", hasItem("location.y: must not be null")))
                .andExpect(jsonPath("$.error.details", hasItem("rating: must be less than or equal to 5.0")))
                .andExpect(jsonPath("$.error.details", hasSize(4)));
    }

    @Test
    void unknownCarTypeReturns400NamingTheField() throws Exception {
        send(post("/rides"), """
                {"userId": "user-1", "pickup": {"x": 0, "y": 0}, "carType": "SUV"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.details[0]").value("carType: invalid value"));
    }

    @Test
    void nonFiniteCoordinateIsRejected() throws Exception {
        send(post("/rides"), """
                {"userId": "user-1", "pickup": {"x": 1e999, "y": 0}, "carType": "SEDAN"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownResourcesReturn404InTheSameShape() throws Exception {
        send(post("/rides/ride-0/end"), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RIDE_NOT_FOUND"));
        mvc.perform(get("/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Ride Hailing Service"))
                .andExpect(jsonPath("$.paths['/rides'].post").exists());
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A grid region far from every other test's region (search radius is 5 km). */
    private static double region() {
        return -900 + REGION.incrementAndGet() * 40.0;   // 45 regions fit in the +-1000 grid
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String registerUser() throws Exception {
        int n = UNIQUE.incrementAndGet();
        return id(send(post("/users"), """
                {"name": "Rider %d", "phone": "9%09d"}""".formatted(n, n))
                .andExpect(status().isCreated()));
    }

    private String registerDriver(String carType, double x, double y) throws Exception {
        int n = UNIQUE.incrementAndGet();
        return id(send(post("/drivers"), """
                {"name": "Driver %d", "phone": "8%09d", "plateNumber": "KA01-%d", "carType": "%s",
                 "location": {"x": %s, "y": %s}}""".formatted(n, n, n, carType, x, y))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rating").value(5.0)));
    }

    private static String locationJson(double x, double y) {
        return """
                {"location": {"x": %s, "y": %s}}""".formatted(x, y);
    }

    private static String bookingJson(String userId, double x, double y, String carType, String couponCode) {
        String coupon = couponCode == null ? "" : ", \"couponCode\": \"%s\"".formatted(couponCode);
        return """
                {"userId": "%s", "pickup": {"x": %s, "y": %s}, "carType": "%s"%s}"""
                .formatted(userId, x, y, carType, coupon);
    }
}
