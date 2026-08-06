package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostViewRepository extends JpaRepository<PostView, Long> {

    @Query("select pv from PostView pv where pv.post.postId = :postId and pv.user.userId = :userId")
    Optional<PostView> findByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

}
