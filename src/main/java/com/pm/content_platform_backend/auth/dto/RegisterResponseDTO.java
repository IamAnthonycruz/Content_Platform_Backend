package com.pm.content_platform_backend.auth.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class RegisterResponseDTO {
    private String username;
    private Instant createdAt;
}
