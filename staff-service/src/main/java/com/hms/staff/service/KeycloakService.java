package com.hms.staff.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class KeycloakService {

    @Value("${keycloak.serverUrl}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.adminUsername}")
    private String adminUsername;

    @Value("${keycloak.adminPassword}")
    private String adminPassword;

    private Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId("admin-cli")
                .build();
    }

    public String createUser(String username, String email, String firstName, String lastName, String password) {
        Keycloak keycloak = getKeycloakInstance();

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        user.setCredentials(Collections.singletonList(credential));

        UsersResource usersResource = keycloak.realm(realm).users();
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            log.info("Created Keycloak user with ID: {}", userId);

            // Assign 'doctor' role
            RoleRepresentation doctorRole = keycloak.realm(realm).roles().get("doctor").toRepresentation();
            usersResource.get(userId).roles().realmLevel().add(Collections.singletonList(doctorRole));

            return userId;
        } else {
            String errorMsg = response.readEntity(String.class);
            log.error("Failed to create Keycloak user: {}. Status: {}", errorMsg, response.getStatus());
            throw new RuntimeException("Failed to create Keycloak user: " + errorMsg);
        }
    }

    public void deactivateUser(String userId) {
        Keycloak keycloak = getKeycloakInstance();
        UserRepresentation user = keycloak.realm(realm).users().get(userId).toRepresentation();
        user.setEnabled(false);
        keycloak.realm(realm).users().get(userId).update(user);
        log.info("Deactivated Keycloak user with ID: {}", userId);
    }
}
