package com.services;

import com.dto.keycloakDto.AccessTokenResponse;
import com.dto.usersDto.CreateUserDTO;
import com.entities.User;
import com.exceptions.KeycloakException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;


    public AccessTokenResponse login(String email, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakServerUrl, realm);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("grant_type", "password");
        map.add("username", email);
        map.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(
                    tokenUrl, request, AccessTokenResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new KeycloakException("Login failed: " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            throw new KeycloakException("Keycloak server error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new KeycloakException("Unknown error during login", e);
        }
    }

    public String createUserInKeycloak(CreateUserDTO userDTO) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(userDTO.getEmail());
        user.setEmail(userDTO.getEmail());
        user.setFirstName(userDTO.getName());
        user.setLastName(userDTO.getSurname());
        user.setEmailVerified(true);

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("birthDate", Collections.singletonList(userDTO.getBirthDate().toString()));
        user.setAttributes(attributes);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(userDTO.getPassword());
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        try {
            Response response = keycloak.realm(realm).users().create(user);

            if (response.getStatus() != 201) {
                String errorBody = response.readEntity(String.class);
                throw new KeycloakException("Failed to create user in Keycloak (" + response.getStatus()
                        + "): " + errorBody);
            }
            return CreatedResponseUtil.getCreatedId(response);
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakException("Error connecting to Keycloak during user creation", e);
        }
    }

    public void updateUserInKeycloak(String keycloakId, User userToUpdate) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        try {
            UserResource userResource = keycloak.realm(realm).users().get(keycloakId);
            UserRepresentation user = userResource.toRepresentation();

            user.setFirstName(userToUpdate.getName());
            user.setLastName(userToUpdate.getSurname());
            if (!userToUpdate.getEmail().equals(user.getEmail())) {
                user.setEmail(userToUpdate.getEmail());
                user.setUsername(userToUpdate.getEmail());
                user.setEmailVerified(true);
            }

            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("birthDate", Collections.singletonList(userToUpdate.getBirthDate().toString()));
            user.setAttributes(attributes);

            userResource.update(user);
        } catch (ClientErrorException e) {
            String errorResponse = e.getResponse().readEntity(String.class);
            throw new KeycloakException("Keycloak update failed: " + errorResponse);
        } catch (Exception e) {
            throw new KeycloakException("Unknown error during Keycloak update", e);
        }
    }

    public AccessTokenResponse refreshToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakServerUrl, realm);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("grant_type", "refresh_token");
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(
                    tokenUrl, request, AccessTokenResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new KeycloakException("Token refresh failed: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new KeycloakException("Unknown error during token refresh", e);
        }
    }

    public void deleteUserInKeycloak(String keycloakId) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        try {
            keycloak.realm(realm).users().delete(keycloakId);
        } catch (ClientErrorException e) {
            if (e.getResponse().getStatus() == 404)
                throw new KeycloakException("Failed to delete user in Keycloak", e);
            throw new KeycloakException("Failed to delete user in Keycloak", e);
        }
    }
}