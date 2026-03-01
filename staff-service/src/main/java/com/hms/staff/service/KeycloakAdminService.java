package com.hms.staff.service;

import com.hms.staff.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final RestTemplate restTemplate;

    @Value("${keycloak.admin.url}")
    private String keycloakUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.enabled:true}")
    private boolean keycloakEnabled;

    // ─── Token Management ─────────────────────────────────────────────────────

    private String getAdminToken() {
        String tokenUrl = keycloakUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
            throw new RuntimeException("Failed to obtain Keycloak admin token");
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token: {}", e.getMessage());
            throw new BusinessException("KEYCLOAK_ERROR", "Failed to connect to Keycloak: " + e.getMessage(), 503);
        }
    }

    private HttpHeaders buildAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ─── User Management ──────────────────────────────────────────────────────

    public String createUser(String username, String email, String firstName, String lastName, String password) {
        if (!keycloakEnabled) {
            log.info("[Keycloak disabled] Skipping user creation for {}", username);
            return "mock-keycloak-id-" + username;
        }

        String token = getAdminToken();
        String usersUrl = keycloakUrl + "/admin/realms/" + realm + "/users";

        Map<String, Object> userRepresentation = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                ))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, buildAuthHeaders(token));

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(usersUrl, request, Void.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                String userId = getUserIdByUsername(token, username);
                log.info("Created Keycloak user '{}' with id {}", username, userId);
                return userId;
            }
            throw new BusinessException("KEYCLOAK_ERROR", "Failed to create Keycloak user", 500);
        } catch (HttpClientErrorException.Conflict e) {
            throw new BusinessException("USERNAME_OR_EMAIL_TAKEN",
                    "Username or email already exists in Keycloak", 409);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating Keycloak user: {}", e.getMessage(), e);
            throw new BusinessException("KEYCLOAK_ERROR", "Failed to create Keycloak user: " + e.getMessage(), 500);
        }
    }

    public void assignRealmRole(String userId, String roleName) {
        if (!keycloakEnabled) return;

        String token = getAdminToken();

        // Get role representation
        String roleUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + roleName;
        HttpEntity<Void> request = new HttpEntity<>(buildAuthHeaders(token));

        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(roleUrl, HttpMethod.GET, request, Map.class);
            if (!roleResponse.getStatusCode().is2xxSuccessful() || roleResponse.getBody() == null) {
                log.warn("Role '{}' not found in Keycloak realm '{}'. Skipping role assignment.", roleName, realm);
                return;
            }

            Map<String, Object> role = roleResponse.getBody();

            // Assign role to user
            String assignUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
            HttpEntity<List<Map<String, Object>>> assignRequest = new HttpEntity<>(List.of(role), buildAuthHeaders(token));
            restTemplate.postForEntity(assignUrl, assignRequest, Void.class);
            log.info("Assigned role '{}' to Keycloak user {}", roleName, userId);
        } catch (Exception e) {
            log.warn("Failed to assign role '{}' to user {}: {}", roleName, userId, e.getMessage());
        }
    }

    public void disableUser(String keycloakUserId) {
        if (!keycloakEnabled) {
            log.info("[Keycloak disabled] Skipping user disable for {}", keycloakUserId);
            return;
        }

        String token = getAdminToken();
        String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId;

        Map<String, Object> update = Map.of("enabled", false);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(update, buildAuthHeaders(token));

        try {
            restTemplate.exchange(userUrl, HttpMethod.PUT, request, Void.class);
            log.info("Disabled Keycloak user {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to disable Keycloak user {}: {}", keycloakUserId, e.getMessage(), e);
            throw new BusinessException("KEYCLOAK_ERROR", "Failed to disable Keycloak account", 500);
        }
    }

    public void updateUserEmail(String keycloakUserId, String email) {
        if (!keycloakEnabled) return;

        String token = getAdminToken();
        String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId;

        Map<String, Object> update = Map.of("email", email);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(update, buildAuthHeaders(token));

        try {
            restTemplate.exchange(userUrl, HttpMethod.PUT, request, Void.class);
        } catch (Exception e) {
            log.warn("Failed to update Keycloak user email: {}", e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getUserIdByUsername(String token, String username) {
        String searchUrl = keycloakUrl + "/admin/realms/" + realm + "/users?username=" + username + "&exact=true";
        HttpEntity<Void> request = new HttpEntity<>(buildAuthHeaders(token));

        try {
            ResponseEntity<List> response = restTemplate.exchange(searchUrl, HttpMethod.GET, request, List.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && !response.getBody().isEmpty()) {
                Map<String, Object> user = (Map<String, Object>) response.getBody().get(0);
                return (String) user.get("id");
            }
        } catch (Exception e) {
            log.error("Failed to fetch Keycloak user id for {}: {}", username, e.getMessage());
        }
        return null;
    }
}
