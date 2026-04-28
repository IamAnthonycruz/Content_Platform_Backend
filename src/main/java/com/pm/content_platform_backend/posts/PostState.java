package com.pm.content_platform_backend.posts;

public interface PostState {
    Post publish(Post post);
    Post archive(Post post);
}
