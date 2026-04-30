package com.pm.content_platform_backend.posts.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_name", nullable = false)
    private String name;

    @Column(name = "tag_slug", nullable = false, unique = true)
    private String slug;

    @ManyToMany(mappedBy = "tags")
    private Set<Post> posts = new HashSet<>();
}
