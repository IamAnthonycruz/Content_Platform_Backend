package com.pm.content_platform_backend.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class RefreshRequestDTO {
    @NotBlank
    private String refreshToken;
}
