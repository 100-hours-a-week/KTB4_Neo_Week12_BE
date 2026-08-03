package com.ktb.community.domain.comment.repository;

import com.ktb.community.domain.comment.entity.Comment;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    boolean existsByPostAndUserAndDeletedFalse(Post post, User user);

    @Query("""
            select c
            from Comment c
            join fetch c.user
            where c.post = :post
              and (c.parentComment is null or c.deleted = false)
            order by c.createdAt asc
            """)
    List<Comment> findCommentsWithUserByPost(@Param("post") Post post);

    @Query("""
            select distinct c.post.postId
            from Comment c
            where c.user.userId = :userId
              and c.deleted = false
              and c.post.postId in :postIds
            """)
    List<Long> findCommentedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") Collection<Long> postIds
    );

}
