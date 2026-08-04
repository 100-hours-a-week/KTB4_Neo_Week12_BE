package com.ktb.community.domain.draft.scheduler;

import com.ktb.community.domain.draft.service.DraftCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftCleanupScheduler {

    private final DraftCleanupService draftCleanupService;

    @Scheduled(
            fixedDelayString = "${draft.cleanup-interval}",
            initialDelayString = "${draft.cleanup-interval}"
    )
    public void cleanupExpiredDrafts() {
        try {
            int deletedCount =
                    draftCleanupService.cleanupExpiredDrafts();

            log.info(
                    "Cleaned up expired Drafts. count={}",
                    deletedCount
            );
        } catch (RuntimeException error) {
            log.error(
                    "Failed to clean up expired Drafts",
                    error
            );
        }
    }
}