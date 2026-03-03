package com.hms.gateway.model;

import java.util.Set;

/**
 * Represents a single RBAC routing rule: a path pattern and the roles allowed to access it.
 * Rules are evaluated in order (most specific first) using AntPathMatcher.
 */
public record RouteRule(String pattern, Set<String> allowedRoles) {

    /** Convenience constructor — accepts varargs roles instead of a Set. */
    public RouteRule(String pattern, String... roles) {
        this(pattern, Set.of(roles));
    }

    /** Returns true if the given role is permitted by this rule. */
    public boolean allowsRole(String role) {
        return role != null && allowedRoles.contains(role);
    }
}
