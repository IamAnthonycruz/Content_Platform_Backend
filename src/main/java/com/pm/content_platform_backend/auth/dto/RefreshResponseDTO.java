package com.pm.content_platform_backend.auth.dto;

import lombok.Data;

@Data
public class RefreshResponseDTO {
    private String accessToken;
    private Long expiresIn;
    private String tokenType="Bearer";
}
