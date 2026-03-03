package com.hms.gateway.filter;

import com.hms.gateway.model.RouteRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GlobalFilter — Keycloak JWT validation + RBAC enforcement.
 * Runs after RateLimitFilter (order HIGHEST_PRECEDENCE + 100).
 *
 * Public path skipped: /health
 * For all other paths:
 *   1. Extract Bearer token → 401 if missing
 *   2. Decode + validate via Keycloak JWKS (signature, expiry) → 401 if invalid
 *   3. Extract HMS role from realm_access.roles[] → 401 if no recognised role
 *   4. RBAC: match path against RouteRule list → 403 if role not allowed
 *   5. Mutate request: add X-User-Id/Role/Name, remove Authorization
 *
 * No Redis blacklist — Keycloak handles token revocation via short access-token TTL
 * and refresh-token revocation on logout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final ReactiveJwtDecoder jwtDecoder;
    private final AntPathMatcher     pathMatcher = new AntPathMatcher();

    /** The six HMS roles we recognise — Keycloak tokens may contain additional system roles. */
    private static final Set<String> HMS_ROLES = Set.of(
            "admin", "doctor", "nurse", "receptionist", "lab_tech", "ward_staff"
    );

    // ── RBAC route rules (most specific patterns first) ───────────────────────
    private static final List<RouteRule> ROUTE_RULES = List.of(
        new RouteRule("/patients/*/vitals-history", "admin", "doctor", "nurse", "receptionist"),
        new RouteRule("/patients/*/history",        "admin", "doctor", "nurse", "receptionist"),
        new RouteRule("/patients/*/admissions",     "admin", "ward_staff", "doctor", "nurse"),
        new RouteRule("/patients/**",               "admin", "receptionist", "doctor", "nurse"),
        new RouteRule("/tokens/**",                 "admin", "receptionist"),
        new RouteRule("/queue/**",                  "admin", "receptionist", "doctor", "nurse"),
        new RouteRule("/appointments/**",           "admin", "receptionist", "doctor"),
        new RouteRule("/sessions/**",               "admin", "doctor", "nurse"),
        new RouteRule("/pharmacy/**",               "admin", "doctor", "nurse"),
        new RouteRule("/doctors/**",                "admin"),
        new RouteRule("/departments/**",            "admin"),
        new RouteRule("/schedules/**",              "admin"),
        new RouteRule("/nurse-allocations/**",      "admin", "nurse"),
        new RouteRule("/staff/**",                  "admin"),
        new RouteRule("/invoices/**",               "admin", "receptionist"),
        new RouteRule("/reports/**",                "admin"),
        new RouteRule("/prescriptions/**",          "admin", "receptionist"),
        new RouteRule("/tests/**",                  "admin", "lab_tech"),
        new RouteRule("/orders/**",                 "admin", "lab_tech", "doctor"),
        new RouteRule("/wards/**",                  "admin", "ward_staff"),
        new RouteRule("/beds/**",                   "admin", "ward_staff"),
        new RouteRule("/admissions/**",             "admin", "ward_staff", "doctor", "nurse")
    );

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // /health is public — no token required
        if (path.equals("/health")) {
            return chain.filter(exchange);
        }

        // Find matching RBAC rule (404 if no route exists for this path)
        RouteRule rule = findRule(path);
        if (rule == null) {
            return writeError(exchange, HttpStatus.NOT_FOUND,
                    "Cannot " + exchange.getRequest().getMethod() + " " + path);
        }

        // Extract Bearer token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Authorization header missing or malformed");
        }
        String token = authHeader.substring(7);

        // Decode and validate the Keycloak JWT (signature + expiry checked by NimbusReactiveJwtDecoder)
        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    // Extract HMS role from Keycloak's realm_access.roles[] claim
                    String role = extractHmsRole(jwt.getClaimAsMap("realm_access"));
                    if (role == null) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED,
                                "Token does not contain a recognised HMS role");
                    }

                    // RBAC check
                    if (!rule.allowsRole(role)) {
                        log.debug("[RBAC] Role '{}' denied access to {}", role, path);
                        return writeError(exchange, HttpStatus.FORBIDDEN,
                                "Role '" + role + "' is not allowed to access this resource");
                    }

                    // Resolve display name: prefer 'name' claim, fall back to 'preferred_username'
                    String userId   = jwt.getSubject();
                    String userName = jwt.getClaimAsString("name");
                    if (userName == null || userName.isBlank()) {
                        userName = jwt.getClaimAsString("preferred_username");
                    }
                    final String finalUserName = userName != null ? userName : "";
                    final String finalRole     = role;

                    // Inject X-User-* headers and strip Authorization before forwarding
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.set("X-User-Id",   userId);
                                headers.set("X-User-Role", finalRole);
                                headers.set("X-User-Name", finalUserName);
                                headers.remove(HttpHeaders.AUTHORIZATION);
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(e -> {
                    // Catches JwtValidationException, BadJwtException, and any other JWT error
                    log.debug("[JWT] Validation failed for {}: {}", path, e.getMessage());
                    String msg = e.getMessage() != null && e.getMessage().contains("expired")
                            ? "Token has expired" : "Invalid or unrecognised token";
                    return writeError(exchange, HttpStatus.UNAUTHORIZED, msg);
                });
    }

    /**
     * Extracts the first HMS role found in the Keycloak realm_access.roles[] array.
     * Keycloak tokens always include system roles ("default-roles-hms", "offline_access") —
     * we filter those out by checking against HMS_ROLES.
     */
    @SuppressWarnings("unchecked")
    private String extractHmsRole(Map<String, Object> realmAccess) {
        if (realmAccess == null) return null;
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List)) return null;
        return ((List<String>) rolesObj).stream()
                .filter(HMS_ROLES::contains)
                .findFirst()
                .orElse(null);
    }

    private RouteRule findRule(String path) {
        return ROUTE_RULES.stream()
                .filter(r -> pathMatcher.match(r.pattern(), path))
                .findFirst()
                .orElse(null);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String safeMsg = message.replace("\"", "'");
        String body = String.format(
                "{\"success\":false,\"error\":{\"message\":\"%s\",\"statusCode\":%d}}",
                safeMsg, status.value());
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
