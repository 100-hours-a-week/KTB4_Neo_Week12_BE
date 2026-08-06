package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    Page<Post> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    @Query("select count(p) from Post p where p.user.userId = :userId and p.createdAt > :time")
    long countByUserIdAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("time") LocalDateTime time
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.likes = p.likes + 1
            where p.postId = :postId
                and p.deleted = false
            """)
    int increaseLikes(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.likes = p.likes - 1
            where p.postId = :postId
              and p.deleted = false
              and p.likes > 0
            """)
    int decreaseLikes(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.comments = p.comments + 1
            where p.postId = :postId
              and p.deleted = false
            """)
    int increaseComments(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Post p
            set p.comments = p.comments - 1
            where p.postId = :postId
              and p.deleted = false
              and p.comments > 0
            """)
    int decreaseComments(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Post p
            set p.views = p.views + 1
            where p.postId = :postId
              and p.deleted = false
            """)
    int increaseViews(@Param("postId") Long postId);

    @Query("""
            select p.likes
            from Post p
            where p.postId = :postId
              and p.deleted = false
            """)
    int findLikeCount(@Param("postId") Long postId);
}
