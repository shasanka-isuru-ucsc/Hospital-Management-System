package com.hms.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Configures JWT decoding using Keycloak's JWKS endpoint.
 * The gateway fetches Keycloak's public key at runtime — no shared secret required.
 * Token validation (signature + expiry) is handled automatically by NimbusReactiveJwtDecoder.
 */
@Configuration
public class KeycloakConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${keycloak.jwks-uri}") String jwksUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
