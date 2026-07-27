package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.PostEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostEditHistoryRepository extends JpaRepository<PostEditHistory, Long> {

    long countByPostId(Long postId);
}
