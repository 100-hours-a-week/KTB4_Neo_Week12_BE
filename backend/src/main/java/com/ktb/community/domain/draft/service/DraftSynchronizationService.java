package com.ktb.community.domain.draft.service;

import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.draft.repository.DraftCache;
import com.ktb.community.domain.draft.repository.DraftRedisRepository;
import com.ktb.community.domain.draft.repository.DraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftSynchronizationService {

    private final DraftRepository draftRepository;
    private final DraftRedisRepository draftRedisRepository;

    @Transactional
    public void synchronizeDraft(
            Long draftId
    ) {
        Optional<DraftCache> redisCacheOptional =
                draftRedisRepository.findById(
                        draftId
                );

        if (redisCacheOptional.isEmpty()) {
            removeOrphanDirty(
                    draftId
            );

            return;
        }

        DraftCache redisCache =
                redisCacheOptional.get();

        Optional<Draft> draftOptional =
                draftRepository.findById(
                        draftId
                );

        if (draftOptional.isEmpty()) {
            deleteOrphanRedisDraft(
                    draftId
            );

            return;
        }

        Draft draft =
                draftOptional.get();

        if (!draft.isActive()) {
            deleteInactiveRedisDraft(
                    draftId,
                    draft.getStatus().name()
            );

            return;
        }

        synchronizeActiveDraft(
                draft,
                redisCache
        );
    }

    private void removeOrphanDirty(Long draftId) {
        boolean removed =
                draftRedisRepository
                        .removeDirtyIfVersionMatches(
                                draftId,
                                0L
                        );

        if (removed) {
            log.debug(
                    "Removed orphan dirty entry. draftId={}",
                    draftId
            );
        }
    }

    private void deleteOrphanRedisDraft(Long draftId) {
        draftRedisRepository.deleteAll(
                draftId
        );

        log.warn(
                "Deleted Redis Draft because RDB Draft does not exist. draftId={}",
                draftId
        );
    }

    private void deleteInactiveRedisDraft(Long draftId, String status) {
        draftRedisRepository.deleteAll(
                draftId
        );

        log.debug(
                "Deleted Redis data for inactive Draft. draftId={}, status={}",
                draftId,
                status
        );
    }

    private void synchronizeActiveDraft(Draft draft, DraftCache redisCache) {
        long redisContentVersion =
                redisCache.contentVersion();

        long rdbContentVersion =
                draft.getContentVersion();

        if (redisContentVersion
                < rdbContentVersion) {
            discardStaleRedisDraft(
                    draft.getDraftId(),
                    redisContentVersion,
                    rdbContentVersion
            );

            return;
        }

        if (redisContentVersion
                == rdbContentVersion) {
            handleEqualVersion(
                    draft,
                    redisCache
            );

            return;
        }

        persistRedisSnapshot(
                draft,
                redisCache
        );
    }

    private void discardStaleRedisDraft(
            Long draftId,
            long redisContentVersion,
            long rdbContentVersion
    ) {
        boolean deleted =
                draftRedisRepository
                        .removeDirtyIfVersionMatches(
                                draftId,
                                rdbContentVersion
                        );

        if (deleted) {
            log.warn(
                    "Deleted stale Redis Draft. draftId={}, redisContentVersion={}, rdbContentVersion={}",
                    draftId,
                    redisContentVersion,
                    rdbContentVersion
            );
            return;
        }

        log.debug(
                "Stale Redis Draft was not deleted because its version changed. draftId={}, observedRedisVersion={}",
                draftId,
                redisContentVersion
        );
    }

    private void handleEqualVersion(
            Draft draft,
            DraftCache redisCache
    ) {
        DraftCache rdbCache =
                toRdbCache(draft);

        if (!redisCache.hasSameContent(rdbCache)) {
            log.warn(
                    "Draft content conflict during synchronization. draftId={}, contentVersion={}",
                    draft.getDraftId(),
                    draft.getContentVersion()
            );

            return;
        }

        boolean removed =
                draftRedisRepository
                        .removeDirtyIfVersionMatches(
                                draft.getDraftId(),
                                draft.getContentVersion()
                        );

        if (removed) {
            log.debug(
                    "Removed already synchronized dirty entry. draftId={}, contentVersion={}",
                    draft.getDraftId(),
                    draft.getContentVersion()
            );
        }
    }

    private DraftCache toRdbCache(
            Draft draft
    ) {
        return new DraftCache(
                draft.getDraftId(),
                draft.getTitle(),
                draft.getPostBody(),
                draft.getPostImage(),
                draft.getContentVersion(),
                draft.getRdbSavedAt()
        );
    }

    private void persistRedisSnapshot(
            Draft draft,
            DraftCache redisCache
    ) {
        LocalDateTime rdbSavedAt =
                LocalDateTime.now();

        draft.saveSnapshot(
                redisCache.title(),
                redisCache.postBody(),
                redisCache.postImage(),
                redisCache.contentVersion(),
                rdbSavedAt
        );

        draftRepository.flush();

        long savedContentVersion =
                draft.getContentVersion();

        removeDirtyAfterCommit(
                draft.getDraftId(),
                savedContentVersion
        );
    }

    private void removeDirtyAfterCommit(
            Long draftId,
            long savedContentVersion
    ) {
        registerAfterCommit(
                () -> {
                    try {
                        boolean removed =
                                draftRedisRepository
                                        .removeDirtyIfVersionMatches(
                                                draftId,
                                                savedContentVersion
                                        );

                        if (!removed) {
                            log.debug(
                                    "Retained dirty Draft because Redis has a newer version. draftId={}, savedContentVersion={}",
                                    draftId,
                                    savedContentVersion
                            );
                        }
                    } catch (RuntimeException e) {
                        log.warn(
                                "Failed to remove synchronized dirty Draft. draftId={}, savedContentVersion={}",
                                draftId,
                                savedContentVersion,
                                e
                        );
                    }
                }
        );
    }

    private void registerAfterCommit(
            Runnable action
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                action.run();
                            }
                        }
                );
    }
}
