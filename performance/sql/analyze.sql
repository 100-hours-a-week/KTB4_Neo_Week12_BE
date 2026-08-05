ANALYZE TABLE users, posts, comments;

SELECT 'B-1: Posts First Page' AS test_case;

EXPLAIN ANALYZE
SELECT
    p.post_id,
    p.title,
    p.created_at,
    p.user_id
FROM posts p
WHERE p.deleted = FALSE
ORDER BY p.created_at DESC
    LIMIT 20 OFFSET 0;

SELECT 'B-2: Posts Deep Page' AS test_case;

EXPLAIN ANALYZE
SELECT
    p.post_id,
    p.title,
    p.created_at,
    p.user_id
FROM posts p
WHERE p.deleted = FALSE
ORDER BY p.created_at DESC
    LIMIT 20 OFFSET 80000;