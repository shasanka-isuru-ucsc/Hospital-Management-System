package com.hms.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GlobalFilter — Rate limiting via Redis INCR/EXPIRE.
 * Limit: 100 requests per 60 seconds per client IP.
 * Runs first (HIGHEST_PRECEDENCE) before JWT validation.
 * Fails open: if Redis is unavailable, the request is allowed through.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int    MAX_REQUESTS   = 100;
    private static final Duration WINDOW       = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip  = resolveClientIp(exchange);
        String key = "rl:" + ip;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    // On the very first request in this window, set the expiry
                    if (count == 1) {
                        return redisTemplate.expire(key, WINDOW).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > MAX_REQUESTS) {
                        log.warn("[RateLimit] IP {} exceeded limit ({} req/min)", ip, MAX_REQUESTS);
                        return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                "Too many requests — limit is " + MAX_REQUESTS + " per minute");
                    }
                    return chain.filter(exchange);
                })
                // Fail open: if Redis is down we still let the request through
                .onErrorResume(err -> {
                    log.error("[RateLimit] Redis error — failing open: {}", err.getMessage());
                    return chain.filter(exchange);
                });
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"success\":false,\"error\":{\"message\":\"%s\",\"statusCode\":%d}}",
                message, status.value());
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
