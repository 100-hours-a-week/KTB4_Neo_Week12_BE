package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    Page<Post> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime time);
}
