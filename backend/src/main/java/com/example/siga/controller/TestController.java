package com.example.siga.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class TestController {

    @GetMapping
    public Map<String, Object> getMe(@AuthenticationPrincipal Jwt jwt) {
        // Extraemos la información del token JWT que nos envió Keycloak
        return Map.of(
                "username", jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email"),
                "roles", jwt.getClaimAsMap("realm_access").get("roles")
        );
    }
}
