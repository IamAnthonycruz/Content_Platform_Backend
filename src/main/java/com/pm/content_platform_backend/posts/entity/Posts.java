package com.pm.content_platform_backend.posts.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Posts {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "post_title", nullable = false)
    private String title;

    @Column(name = "post_body", nullable = false)
    private String body;

    @Column(name = "post_excerpt", nullable = false)
    private String excerpt;

    @Column(name = "post_status", nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreationTimestamp
    @Column(name = "created_at",nullable = false)
    private Instant createdAt;






    //One author can have many posts

}
