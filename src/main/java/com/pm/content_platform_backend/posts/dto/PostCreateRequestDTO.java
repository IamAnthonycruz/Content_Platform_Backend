package com.pm.content_platform_backend.posts.dto;

import com.pm.content_platform_backend.posts.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateRequestDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String body;
    @NotBlank
    private String excerpt;
    
    private List<String> tags;


}
