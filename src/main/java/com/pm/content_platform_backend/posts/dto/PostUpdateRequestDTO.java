package com.pm.content_platform_backend.posts.dto;

import com.pm.content_platform_backend.posts.entity.Status;
import lombok.Data;

import java.util.List;

@Data
public class PostUpdateRequestDTO {

    private String title;

    private String body;

    private String excerpt;

    private Status status;

    private List<String> tags;

}
