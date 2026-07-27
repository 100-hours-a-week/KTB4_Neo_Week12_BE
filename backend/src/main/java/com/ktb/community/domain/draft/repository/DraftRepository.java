package com.ktb.community.domain.draft.repository;

import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    Optional<Draft> findByDraftIdAndUser(Long draftId, User user);
}