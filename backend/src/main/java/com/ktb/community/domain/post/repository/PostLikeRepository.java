package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.entity.PostLike;
import com.ktb.community.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostAndUser(Post post, User user);

    Optional<PostLike> findByPostAndUser(Post post, User user);

    @Query("""
            select pl.post.postId
            from PostLike pl
            where pl.user.userId = :userId
              and pl.post.postId in :postIds
            """)
    List<Long> findLikedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        delete from PostLike pl
        where pl.post.postId = :postId
          and pl.user.userId = :userId
        """)
    int deleteByPostIdAndUserId(
            @Param("postId") Long postId,
            @Param("userId") Long userId
    );
}
