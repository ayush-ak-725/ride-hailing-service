package com.ridehailing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rideHailingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ride Hailing Service")
                .version("0.1.0")
                .description("""
                        In-memory ride hailing backend. Locations are (x, y) points on a city grid in km. \
                        Errors use {"error": {"code", "message", "details"}}. No authentication (out of scope)."""));
    }
}
