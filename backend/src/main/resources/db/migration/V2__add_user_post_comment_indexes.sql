CREATE INDEX idx_users_email_deleted
    ON users (email, deleted);

CREATE INDEX idx_users_nickname_deleted
    ON users (nickname, deleted);

CREATE INDEX idx_posts_deleted_created_at
    ON posts (deleted, created_at DESC);

CREATE INDEX idx_posts_user_created_at
    ON posts (user_id, created_at);

CREATE INDEX idx_comments_post_created_at
    ON comments (post_id, created_at);

CREATE INDEX idx_comments_user_deleted_post
    ON comments (user_id, deleted, post_id);
