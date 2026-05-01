package com.pm.content_platform_backend.posts.repository;

import com.pm.content_platform_backend.posts.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
}
