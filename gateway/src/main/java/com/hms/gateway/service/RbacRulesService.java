package com.hms.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.gateway.model.RouteRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages RBAC route rules backed by Redis.
 *
 * On startup: loads rules from Redis key "hms:rbac:rules".
 *   - If the key exists: hydrates the in-memory cache from Redis.
 *   - If the key is absent: seeds Redis with DEFAULT_RULES and uses them.
 *
 * All mutations (add / update / delete / reset) persist to Redis and
 * atomically update the in-memory cache — zero downtime, no restart needed.
 *
 * JwtAuthenticationFilter reads getRules() which is always served from the
 * in-memory cache (no Redis round-trip per request).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacRulesService {

    static final String REDIS_KEY = "hms:rbac:rules";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper                objectMapper;
    private final AntPathMatcher             pathMatcher = new AntPathMatcher();

    /** Thread-safe in-memory cache — always non-null. */
    private final AtomicReference<List<RouteRule>> cache =
            new AtomicReference<>(new ArrayList<>(DEFAULT_RULES));

    // ── Default rules (built-in fallback, most-specific patterns first) ───────
    public static final List<RouteRule> DEFAULT_RULES = List.of(
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

    // ── Startup ───────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        redisTemplate.opsForValue().get(REDIS_KEY)
                .flatMap(json -> {
                    try {
                        List<RouteRule> rules = objectMapper.readValue(
                                json, new TypeReference<>() {});
                        cache.set(rules);
                        log.info("[RBAC] Loaded {} rules from Redis", rules.size());
                        return Mono.just(rules);
                    } catch (Exception e) {
                        log.warn("[RBAC] Could not parse rules from Redis ({}), reseeding defaults", e.getMessage());
                        return persistAndCache(new ArrayList<>(DEFAULT_RULES));
                    }
                })
                .switchIfEmpty(
                    // Key doesn't exist — seed Redis with defaults
                    persistAndCache(new ArrayList<>(DEFAULT_RULES))
                        .doOnSuccess(r -> log.info("[RBAC] Seeded {} default rules into Redis", r.size()))
                )
                .onErrorResume(err -> {
                    log.warn("[RBAC] Redis unavailable at startup, using in-memory defaults: {}", err.getMessage());
                    return Mono.just(cache.get());
                })
                .subscribe();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns the current cached rule list (no Redis round-trip). */
    public List<RouteRule> getRules() {
        return cache.get();
    }

    /** Find the first rule whose pattern matches the given path. Returns null if none. */
    public RouteRule findMatchingRule(String path) {
        return cache.get().stream()
                .filter(r -> pathMatcher.match(r.pattern(), path))
                .findFirst()
                .orElse(null);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Add a new rule (or replace an existing rule for the same pattern).
     * Position: 0 = first, negative or >= size = last.
     */
    public Mono<List<RouteRule>> addRule(RouteRule rule, int position) {
        List<RouteRule> updated = new ArrayList<>(cache.get());
        // Remove any existing rule for the same pattern first
        updated.removeIf(r -> r.pattern().equals(rule.pattern()));
        int pos = (position < 0 || position > updated.size()) ? updated.size() : position;
        updated.add(pos, rule);
        return persistAndCache(updated);
    }

    /**
     * Replace the roles for the rule at the given index.
     */
    public Mono<List<RouteRule>> updateRule(int index, Set<String> newRoles) {
        List<RouteRule> current = cache.get();
        if (index < 0 || index >= current.size()) {
            return Mono.error(new IndexOutOfBoundsException("Index " + index + " is out of range (size=" + current.size() + ")"));
        }
        List<RouteRule> updated = new ArrayList<>(current);
        updated.set(index, new RouteRule(current.get(index).pattern(), newRoles));
        return persistAndCache(updated);
    }

    /**
     * Remove the rule at the given index.
     */
    public Mono<List<RouteRule>> deleteRule(int index) {
        List<RouteRule> current = cache.get();
        if (index < 0 || index >= current.size()) {
            return Mono.error(new IndexOutOfBoundsException("Index " + index + " is out of range (size=" + current.size() + ")"));
        }
        List<RouteRule> updated = new ArrayList<>(current);
        updated.remove(index);
        return persistAndCache(updated);
    }

    /**
     * Replace the entire rule list (used for reordering or bulk updates).
     */
    public Mono<List<RouteRule>> replaceAll(List<RouteRule> rules) {
        return persistAndCache(new ArrayList<>(rules));
    }

    /**
     * Reload rules from Redis into the in-memory cache.
     * Useful if rules were modified externally (e.g. by another gateway instance).
     */
    public Mono<List<RouteRule>> reload() {
        return redisTemplate.opsForValue().get(REDIS_KEY)
                .flatMap(json -> {
                    try {
                        List<RouteRule> rules = objectMapper.readValue(json, new TypeReference<>() {});
                        cache.set(rules);
                        log.info("[RBAC] Reloaded {} rules from Redis", rules.size());
                        return Mono.just(rules);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to parse rules from Redis: " + e.getMessage()));
                    }
                })
                .switchIfEmpty(Mono.error(new RuntimeException("No rules found in Redis")));
    }

    /**
     * Reset to the built-in defaults, overwriting whatever is in Redis.
     */
    public Mono<List<RouteRule>> resetToDefaults() {
        log.info("[RBAC] Resetting to {} default rules", DEFAULT_RULES.size());
        return persistAndCache(new ArrayList<>(DEFAULT_RULES));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Mono<List<RouteRule>> persistAndCache(List<RouteRule> rules) {
        try {
            String json = objectMapper.writeValueAsString(rules);
            return redisTemplate.opsForValue().set(REDIS_KEY, json)
                    .doOnSuccess(ok -> cache.set(rules))
                    .thenReturn(rules);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to serialize rules: " + e.getMessage()));
        }
    }
}
