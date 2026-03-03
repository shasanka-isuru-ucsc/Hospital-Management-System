package com.hms.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebFilter (not GlobalFilter) that protects /admin/** endpoints.
 *
 * WHY a WebFilter and not a GlobalFilter:
 *   GlobalFilters in Spring Cloud Gateway only run for requests that match a
 *   gateway proxy route. Local @RestController endpoints (like AdminRoutesController)
 *   bypass the proxy pipeline entirely, so GlobalFilters never see them.
 *   WebFilters run for ALL incoming requests — both proxied and local controllers.
 *
 * This filter is ordered at HIGHEST_PRECEDENCE+10, which runs before the gateway's
 * own GlobalFilter wrappers. For paths that do NOT start with /admin/, it
 * immediately delegates to the next filter in the chain — zero overhead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminAuthFilter implements WebFilter {

    private final ReactiveJwtDecoder jwtDecoder;

    private static final Set<String> HMS_ROLES = Set.of(
            "admin", "doctor", "nurse", "receptionist", "lab_tech", "ward_staff"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Only protect /admin/** — everything else passes straight through
        if (!path.startsWith("/admin/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Authorization header missing or malformed");
        }
        String token = authHeader.substring(7);

        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    String role = extractHmsRole(jwt.getClaimAsMap("realm_access"));
                    if (!"admin".equals(role)) {
                        log.debug("[AdminAuth] Denied access to {} for role '{}'", path, role);
                        return writeError(exchange, HttpStatus.FORBIDDEN,
                                "Admin role required to access " + path);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(BadJwtException.class, e -> {
                    log.debug("[AdminAuth] Invalid token for {}: {}", path, e.getMessage());
                    String msg = e.getMessage() != null && e.getMessage().contains("expired")
                            ? "Token has expired" : "Invalid or unrecognised token";
                    return writeError(exchange, HttpStatus.UNAUTHORIZED, msg);
                });
    }

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
