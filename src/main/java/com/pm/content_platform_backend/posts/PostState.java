package com.pm.content_platform_backend.posts;

import com.pm.content_platform_backend.posts.entity.Post;

public interface PostState {
    Post publish(Post post);
    Post archive(Post post);
}
