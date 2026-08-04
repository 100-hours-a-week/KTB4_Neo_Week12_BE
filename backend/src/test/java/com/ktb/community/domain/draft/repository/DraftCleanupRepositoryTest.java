package com.ktb.community.domain.draft.repository;

import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class DraftCleanupRepositoryTest {

    @Autowired
    private DraftRepository draftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletesOnlyExpiredPublishedAndDeletedDrafts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(7);

        User publishedUser = userRepository.save(
                new User(
                        "published@example.com",
                        "encoded-password",
                        "published-user",
                        null
                )
        );

        User deletedUser = userRepository.save(
                new User(
                        "deleted@example.com",
                        "encoded-password",
                        "deleted-user",
                        null
                )
        );

        User recentUser = userRepository.save(
                new User(
                        "recent@example.com",
                        "encoded-password",
                        "recent-user",
                        null
                )
        );

        User activeUser = userRepository.save(
                new User(
                        "active@example.com",
                        "encoded-password",
                        "active-user",
                        null
                )
        );

        Draft expiredPublishedDraft = new Draft(
                publishedUser,
                "발행 임시글",
                "본문",
                null,
                1L,
                now.minusDays(10)
        );

        expiredPublishedDraft.publish(
                "발행 임시글",
                "본문",
                null,
                1L,
                1L,
                now.minusDays(10)
        );

        Draft expiredDeletedDraft = new Draft(
                deletedUser,
                "삭제 임시글",
                "본문",
                null,
                1L,
                now.minusDays(10)
        );

        expiredDeletedDraft.delete(
                now.minusDays(10)
        );

        Draft recentPublishedDraft = new Draft(
                recentUser,
                "최근 발행 임시글",
                "본문",
                null,
                1L,
                now.minusDays(1)
        );

        recentPublishedDraft.publish(
                "최근 발행 임시글",
                "본문",
                null,
                1L,
                2L,
                now.minusDays(1)
        );

        Draft activeDraft = new Draft(
                activeUser,
                "활성 임시글",
                "본문",
                null,
                1L,
                now
        );

        draftRepository.saveAllAndFlush(
                List.of(
                        expiredPublishedDraft,
                        expiredDeletedDraft,
                        recentPublishedDraft,
                        activeDraft
                )
        );

        List<Long> expiredDraftIds =
                draftRepository.findExpiredDraftIds(
                        cutoff,
                        PageRequest.of(0, 100)
                );

        assertThat(expiredDraftIds)
                .containsExactlyInAnyOrder(
                        expiredPublishedDraft.getDraftId(),
                        expiredDeletedDraft.getDraftId()
                );

        int deletedCount =
                draftRepository.deleteAllByDraftIds(
                        expiredDraftIds
                );

        entityManager.clear();

        assertThat(deletedCount).isEqualTo(2);

        assertThat(
                draftRepository.findById(
                        expiredPublishedDraft.getDraftId()
                )
        ).isEmpty();

        assertThat(
                draftRepository.findById(
                        expiredDeletedDraft.getDraftId()
                )
        ).isEmpty();

        assertThat(
                draftRepository.findById(
                        recentPublishedDraft.getDraftId()
                )
        ).isPresent();

        assertThat(
                draftRepository.findById(
                        activeDraft.getDraftId()
                )
        ).isPresent();
    }
}