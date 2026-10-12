package com.ridehailing.api;

import com.jayway.jsonpath.JsonPath;
import com.ridehailing.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logs are part of the operational contract: every line carries the request id, state changes
 * and rejections are each logged once, and personal data stays out. Uses its own grid area
 * (x ~ 950) and phone prefix because the application context is shared with other API tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoggingAndCorrelationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;

    @Test
    void reusesASafeCallerRequestIdAndTagsTheLogsWithIt(CapturedOutput output) throws Exception {
        String phone = "7000000101";
        String userId = JsonPath.read(mvc.perform(post("/users").header(RequestLoggingFilter.HEADER, "trace-abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Logged\", \"phone\": \"" + phone + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestLoggingFilter.HEADER, "trace-abc-123"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(output).contains("[trace-abc-123] ")
                .contains("User registered: userId=" + userId)
                .contains("POST /users -> 201")
                .doesNotContain(phone);
    }

    @Test
    void replacesAnUnsafeRequestIdWithAGeneratedOne() throws Exception {
        String returned = mvc.perform(post("/coupons").header(RequestLoggingFilter.HEADER, "bad id; DROP")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"LOGCHK1\", \"percentOff\": 5}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(RequestLoggingFilter.HEADER);

        assertThat(returned).matches(UUID_PATTERN);
    }

    @Test
    void logsBookingAndCompletionWithoutPersonalData(CapturedOutput output) throws Exception {
        String phone = "7000000202";
        String userId = JsonPath.read(mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Rider\", \"phone\": \"" + phone + "\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        String driverId = JsonPath.read(mvc.perform(post("/drivers").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "D", "phone": "7000000303", "plateNumber": "LOG-1", "carType": "SEDAN",
                         "location": {"x": 950, "y": 0}}""")).andReturn().getResponse().getContentAsString(), "$.id");
        String rideId = JsonPath.read(mvc.perform(post("/rides").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"" + userId + "\", \"pickup\": {\"x\": 950, \"y\": 0}, \"carType\": \"HATCHBACK\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/rides/" + rideId + "/end")).andExpect(status().isOk());

        assertThat(output)
                .contains("No HATCHBACK available near Location[x=950.0, y=0.0]; trying free upgrade to SEDAN")
                .contains("Ride booked: rideId=" + rideId + " userId=" + userId + " driverId=" + driverId
                        + " requested=HATCHBACK assigned=SEDAN upgraded=true")
                .contains("Ride completed: rideId=" + rideId)
                .contains("total=50.00")
                .doesNotContain(phone)
                .doesNotContain("7000000303");
    }

    @Test
    void logsEachRejectionOnceWithItsCode(CapturedOutput output) throws Exception {
        mvc.perform(post("/rides/ride-does-not-exist/end")).andExpect(status().isNotFound());

        assertThat(output).containsOnlyOnce("Request rejected: code=RIDE_NOT_FOUND message=Ride ride-does-not-exist not found");
        assertThat(output).contains("POST /rides/ride-does-not-exist/end -> 404");
    }

    @Test
    void neutralisesLineBreaksInLoggedUserInput(CapturedOutput output) throws Exception {
        // A pre-encoded URI, so the server really decodes %0A into a line break in the path variable.
        mvc.perform(post(URI.create("/rides/x%0AFAKE%20LOG%20LINE/end"))).andExpect(status().isNotFound());

        assertThat(output).contains("message=Ride x_FAKE LOG LINE not found")
                .doesNotContain("\nFAKE LOG LINE");
    }

    @Test
    void unexpectedErrorsReturnTheRequestIdWithoutInternals() {
        MDC.put(RequestLoggingFilter.MDC_KEY, "req-500");
        try {
            ResponseEntity<ErrorResponse> response = new ApiExceptionHandler()
                    .handleUnexpected(new IllegalStateException("secret internal detail"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().error().message()).contains("req-500").doesNotContain("secret");
        } finally {
            MDC.remove(RequestLoggingFilter.MDC_KEY);
        }
    }
}
