package com.pm.content_platform_backend.auth.security;

public record UserPrincipal(Long userId, String username, String role) {
}
