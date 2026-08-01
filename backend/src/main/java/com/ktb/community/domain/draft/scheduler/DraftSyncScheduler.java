package com.ktb.community.domain.draft.scheduler;

import com.ktb.community.domain.draft.repository.DraftRedisRepository;
import com.ktb.community.domain.draft.service.DraftSynchronizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class DraftSyncScheduler {

    private final DraftRedisRepository draftRedisRepository;

    private final DraftSynchronizationService draftSynchronizationService;

    private final Duration syncDelay;
    private final int syncBatchSize;

    public DraftSyncScheduler(
            DraftRedisRepository draftRedisRepository,
            DraftSynchronizationService
                    draftSynchronizationService,

            @Value("${draft.sync-delay}")
            Duration syncDelay,

            @Value("${draft.sync-batch-size}")
            int syncBatchSize
    ) {
        if (syncDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "draft.sync-delay must not be negative"
            );
        }

        if (syncBatchSize < 1) {
            throw new IllegalArgumentException(
                    "draft.sync-batch-size must be positive"
            );
        }

        this.draftRedisRepository =
                draftRedisRepository;

        this.draftSynchronizationService =
                draftSynchronizationService;

        this.syncDelay = syncDelay;
        this.syncBatchSize = syncBatchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${draft.sync-interval}",
            initialDelayString =
                    "${draft.sync-interval}"
    )
    public void synchronizeDirtyDrafts() {
        long maxScore =
                Instant.now()
                        .minus(syncDelay)
                        .toEpochMilli();

        List<Long> draftIds;

        try {
            draftIds =
                    draftRedisRepository
                            .findDirtyDraftIds(
                                    maxScore,
                                    syncBatchSize
                            );
        } catch (RuntimeException e) {
            log.error(
                    "Failed to load dirty Draft IDs",
                    e
            );

            return;
        }

        for (Long draftId : draftIds) {
            synchronizeOne(
                    draftId
            );
        }
    }

    private void synchronizeOne(
            Long draftId
    ) {
        try {
            draftSynchronizationService
                    .synchronizeDraft(
                            draftId
                    );
        } catch (RuntimeException e) {
            log.error(
                    "Failed to synchronize Draft. draftId={}",
                    draftId,
                    e
            );
        }
    }
}
