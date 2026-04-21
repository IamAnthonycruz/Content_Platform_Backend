package com.pm.content_platform_backend.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired
    RefreshTokenService service;

    @Test
    void issueAndLookup() {
        String token = service.issue(42L);

        Optional<Long> userId = service.lookUp(token);

        assertTrue(userId.isPresent());
        assertEquals(42L, userId.get());
    }

    @Test
    void lookupMissingReturnsEmpty() {
        Optional<Long> userId = service.lookUp("nonexistent-token");
        assertTrue(userId.isEmpty());
    }

    @Test
    void revokeRemovesToken() {
        String token = service.issue(42L);

        service.revoke(token);

        assertTrue(service.lookUp(token).isEmpty());
    }
}
