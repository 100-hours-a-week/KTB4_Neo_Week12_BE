package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.entity.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    @Query("select (count(pr) > 0) from PostReport pr where pr.post.postId = :postId and pr.user.userId = :userId")
    boolean existsByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    long countByPost(Post post);

}
