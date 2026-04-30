package com.pm.content_platform_backend.posts.entity;

import java.util.Map;
import java.util.Set;

public enum Status {
    DRAFT,
    PUBLISHED,
    ARCHIVED;


    private static final Map<Status, Set<Status>> VALID_TRANSITIONS = Map.of(
            DRAFT, Set.of(PUBLISHED),
            PUBLISHED, Set.of(ARCHIVED),
            ARCHIVED, Set.of()
    );

    public boolean canTransitionTo(Status next){
        return VALID_TRANSITIONS.get(this).contains(next);
    }
}
