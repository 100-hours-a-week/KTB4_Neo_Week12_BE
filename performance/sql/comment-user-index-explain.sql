-- idx_comments_user_deleted_post 검증용 쿼리
-- 실제 옵티마이저 선택과 신규 복합 인덱스 사용/미사용을 같은 조건에서 비교한다.
-- MySQL은 신규 인덱스가 user_id FK를 지원하므로 기존 자동 생성 단일 FK 인덱스를 제거했다.

EXPLAIN ANALYZE
SELECT DISTINCT post_id
FROM comments
WHERE user_id = 98
  AND deleted = FALSE
  AND post_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                  11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

EXPLAIN ANALYZE
SELECT DISTINCT post_id
FROM comments IGNORE INDEX (idx_comments_user_deleted_post,
                            idx_comments_post_created_at)
WHERE user_id = 98
  AND deleted = FALSE
  AND post_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                  11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);

EXPLAIN ANALYZE
SELECT DISTINCT post_id
FROM comments FORCE INDEX (idx_comments_user_deleted_post)
WHERE user_id = 98
  AND deleted = FALSE
  AND post_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                  11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
