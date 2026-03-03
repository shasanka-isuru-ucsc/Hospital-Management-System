package com.hms.gateway.controller;

import com.hms.gateway.model.RouteRule;
import com.hms.gateway.service.RbacRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Admin REST API for managing RBAC route rules at runtime - no restart required.
 * All endpoints require an admin JWT (enforced by AdminAuthFilter).
 *
 * Rule ordering matters: rules are matched top-to-bottom, first match wins.
 * More specific patterns must come before broader ones.
 *
 * Base path: /admin/routes
 *   GET    /admin/routes             - list all rules with their indices
 *   POST   /admin/routes             - add (or replace) a rule
 *   PUT    /admin/routes/{index}     - update the roles for an existing rule
 *   DELETE /admin/routes/{index}     - remove a rule
 *   POST   /admin/routes/reset       - restore built-in default rules
 *   POST   /admin/routes/reload      - reload rules from Redis into memory
 */
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRoutesController {

    private final RbacRulesService rulesService;

    // ── Request body records ──────────────────────────────────────────────────

    /** Body for POST /admin/routes */
    public record AddRuleRequest(
            String      pattern,
            Set<String> allowedRoles,
            Integer     position      // optional; null or -1 = append at end
    ) {}

    /** Body for PUT /admin/routes/{index} */
    public record UpdateRuleRequest(Set<String> allowedRoles) {}

    // ── GET — list all rules ──────────────────────────────────────────────────

    /**
     * Returns the current rule list with their indices.
     * Use the index in PUT/DELETE calls.
     */
    @GetMapping
    public Map<String, Object> listRules() {
        List<RouteRule> rules = rulesService.getRules();
        List<Map<String, Object>> indexed = IntStream.range(0, rules.size())
                .mapToObj(i -> Map.<String, Object>of(
                        "index",        i,
                        "pattern",      rules.get(i).pattern(),
                        "allowedRoles", rules.get(i).allowedRoles()
                ))
                .collect(Collectors.toList());
        return Map.of("success", true, "data", indexed);
    }

    // ── POST — add (or replace) a rule ────────────────────────────────────────

    /**
     * Adds a new rule. If a rule with the same pattern already exists it is
     * removed first, then the new rule is inserted at the requested position.
     *
     * Body: { "pattern": "/new/**", "allowedRoles": ["admin","doctor"], "position": 0 }
     * position is optional (omit or -1 to append at the end).
     */
    @PostMapping
    public Mono<Map<String, Object>> addRule(@RequestBody AddRuleRequest req) {
        if (req.pattern() == null || req.pattern().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "pattern is required"));
        }
        if (req.allowedRoles() == null || req.allowedRoles().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "allowedRoles must not be empty"));
        }
        RouteRule rule     = new RouteRule(req.pattern(), req.allowedRoles());
        int       position = req.position() != null ? req.position() : -1;
        return rulesService.addRule(rule, position)
                .map(rules -> successResponse(rules, "Rule added"));
    }

    // ── PUT — update roles for a rule ─────────────────────────────────────────

    /**
     * Replaces the allowed roles for the rule at the given index.
     * (To change the pattern or reorder, delete and re-add.)
     *
     * Body: { "allowedRoles": ["admin","doctor","nurse"] }
     */
    @PutMapping("/{index}")
    public Mono<Map<String, Object>> updateRule(
            @PathVariable int index,
            @RequestBody  UpdateRuleRequest req) {
        if (req.allowedRoles() == null || req.allowedRoles().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "allowedRoles must not be empty"));
        }
        return rulesService.updateRule(index, req.allowedRoles())
                .map(rules -> successResponse(rules, "Rule updated"))
                .onErrorMap(IndexOutOfBoundsException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    // ── DELETE — remove a rule ────────────────────────────────────────────────

    /**
     * Removes the rule at the given index.
     */
    @DeleteMapping("/{index}")
    public Mono<Map<String, Object>> deleteRule(@PathVariable int index) {
        return rulesService.deleteRule(index)
                .map(rules -> successResponse(rules, "Rule deleted"))
                .onErrorMap(IndexOutOfBoundsException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    // ── POST /reset — restore defaults ───────────────────────────────────────

    /**
     * Restores the 22 built-in default rules, overwriting any custom changes.
     */
    @PostMapping("/reset")
    public Mono<Map<String, Object>> reset() {
        return rulesService.resetToDefaults()
                .map(rules -> successResponse(rules, "Rules reset to defaults"));
    }

    // ── POST /reload — sync in-memory cache from Redis ───────────────────────

    /**
     * Forces a reload of the in-memory rule cache from Redis.
     * Useful when rules have been updated by another gateway instance.
     */
    @PostMapping("/reload")
    public Mono<Map<String, Object>> reload() {
        return rulesService.reload()
                .map(rules -> successResponse(rules, "Rules reloaded from Redis"))
                .onErrorMap(e -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> successResponse(List<RouteRule> rules, String message) {
        List<Map<String, Object>> indexed = IntStream.range(0, rules.size())
                .mapToObj(i -> Map.<String, Object>of(
                        "index",        i,
                        "pattern",      rules.get(i).pattern(),
                        "allowedRoles", rules.get(i).allowedRoles()
                ))
                .collect(Collectors.toList());
        return Map.of(
                "success", true,
                "message", message,
                "data",    indexed
        );
    }
}
