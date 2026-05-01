package com.pm.content_platform_backend.posts.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.UUID;

public class PostDeleteRequestDTO {
    @UUID(message = "Please provide a valid UUID")
    @NotBlank
    private String UUID;
}
