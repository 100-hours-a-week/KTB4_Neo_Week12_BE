CREATE INDEX idx_posts_user_created_at
    ON posts (user_id, created_at);
