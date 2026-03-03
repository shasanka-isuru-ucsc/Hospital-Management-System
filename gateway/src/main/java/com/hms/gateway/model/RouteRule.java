package com.hms.gateway.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a single RBAC routing rule: a URL pattern and the roles allowed to access it.
 * Rules are evaluated in order (most specific first) using AntPathMatcher.
 *
 * Uses a regular class (not record) so Jackson can reliably serialize/deserialize
 * it for Redis storage without requiring ParameterNamesModule configuration.
 */
public class RouteRule {

    @JsonProperty("pattern")
    private final String pattern;

    @JsonProperty("allowedRoles")
    private final Set<String> allowedRoles;

    /** Jackson deserialization constructor. */
    @JsonCreator
    public RouteRule(
            @JsonProperty("pattern")     String pattern,
            @JsonProperty("allowedRoles") Set<String> allowedRoles) {
        this.pattern      = pattern;
        this.allowedRoles = allowedRoles != null ? allowedRoles : new LinkedHashSet<>();
    }

    /** Convenience constructor — accepts varargs roles. */
    public RouteRule(String pattern, String... roles) {
        this.pattern      = pattern;
        this.allowedRoles = new LinkedHashSet<>(Arrays.asList(roles));
    }

    public String     pattern()      { return pattern; }
    public Set<String> allowedRoles() { return allowedRoles; }

    public boolean allowsRole(String role) {
        return role != null && allowedRoles.contains(role);
    }

    @Override
    public String toString() {
        return "RouteRule{pattern='" + pattern + "', allowedRoles=" + allowedRoles + '}';
    }
}
