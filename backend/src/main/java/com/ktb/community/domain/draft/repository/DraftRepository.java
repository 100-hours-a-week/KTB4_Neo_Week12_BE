package com.ktb.community.domain.draft.repository;

import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.draft.entity.DraftStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DraftRepository
        extends JpaRepository<Draft, Long> {

    @Query("select d from Draft d where d.draftId = :draftId and d.user.userId = :userId")
    Optional<Draft> findByDraftIdAndUserId(
            @Param("draftId") Long draftId,
            @Param("userId") Long userId
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

    @Query("""
        select d.draftId
        from Draft d
        where (
                d.status = com.ktb.community.domain.draft.entity.DraftStatus.PUBLISHED
                and d.publishedAt < :cutoff
              )
           or (
                d.status = com.ktb.community.domain.draft.entity.DraftStatus.DELETED
                and d.deletedAt < :cutoff
              )
        order by d.draftId
        """)
    List<Long> findExpiredDraftIds(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from Draft d
        where d.draftId in :draftIds
        """)
    int deleteAllByDraftIds(
            @Param("draftIds") Collection<Long> draftIds
    );
}
