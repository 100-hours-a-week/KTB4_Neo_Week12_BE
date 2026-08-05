-- Active user lookup by email.
EXPLAIN ANALYZE
SELECT user_id
FROM users
WHERE email = 'perf-user-009999@example.com'
  AND deleted = FALSE;

-- Active user lookup by nickname.
EXPLAIN ANALYZE
SELECT user_id
FROM users
WHERE nickname = 'perf-user-009999'
  AND deleted = FALSE;

-- First post page.
EXPLAIN ANALYZE
SELECT post_id
FROM posts
WHERE deleted = FALSE
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;

-- Deep post page.
EXPLAIN ANALYZE
SELECT post_id
FROM posts
WHERE deleted = FALSE
ORDER BY created_at DESC
LIMIT 20 OFFSET 80000;

-- Recent post count for one user.
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM posts
WHERE user_id = 38
  AND created_at > '2026-07-01 00:00:00';

-- Normal post comments.
EXPLAIN ANALYZE
SELECT comment_id
FROM comments
WHERE post_id = 16
  AND (
      parent_comment_id IS NULL
      OR deleted = FALSE
  )
ORDER BY created_at ASC;

-- Medium post comments.
EXPLAIN ANALYZE
SELECT comment_id
FROM comments
WHERE post_id = 2
  AND (
      parent_comment_id IS NULL
      OR deleted = FALSE
  )
ORDER BY created_at ASC;
