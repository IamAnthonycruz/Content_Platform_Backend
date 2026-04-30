CREATE TABLE posts(
    post_id UUID PRIMARY KEY,
    post_title TEXT NOT NULL,
    post_body TEXT NOT NULL,
    post_excerpt TEXT NOT NULL,
    post_status TEXT CHECK ( post_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED') ) DEFAULT 'DRAFT' NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    author_id BIGINT NOT NULL,
    CONSTRAINT fk_posts_author FOREIGN KEY  (author_id) REFERENCES  Users(user_id) ON DELETE CASCADE


)