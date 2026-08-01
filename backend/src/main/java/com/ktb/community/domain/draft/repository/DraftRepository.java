package com.ktb.community.domain.draft.repository;

import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.draft.entity.DraftStatus;
import com.ktb.community.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DraftRepository
        extends JpaRepository<Draft, Long> {

    Optional<Draft> findByDraftIdAndUser(
            Long draftId,
            User user
    );

    Optional<Draft> findByActiveOwnerId(
            Long activeOwnerId
    );

    List<Draft> findByStatusAndPublishedAtBefore(
            DraftStatus status,
            LocalDateTime before,
            Pageable pageable
    );

    List<Draft> findByStatusAndDeletedAtBefore(
            DraftStatus status,
            LocalDateTime before,
            Pageable pageable
    );
}
