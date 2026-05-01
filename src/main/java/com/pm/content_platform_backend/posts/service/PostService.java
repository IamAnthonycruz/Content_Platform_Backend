package com.pm.content_platform_backend.posts.service;

import com.pm.content_platform_backend.posts.dto.PostCreateRequestDTO;
import com.pm.content_platform_backend.posts.entity.Post;

public interface PostService {
    PostResponseDTO createPost(PostCreateRequestDTO postRequestDto);
    PostResponseDTO updatePost(PostUpdateRequestDTO postRequestDto);
    PostResponseDTO archive(PostArchiveRequestDTO postArchiveRequestDTO);
    void deletePost(PostRequestDto postRequestDto);


}
