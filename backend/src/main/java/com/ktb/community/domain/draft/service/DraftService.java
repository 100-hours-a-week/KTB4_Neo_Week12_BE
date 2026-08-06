package com.ktb.community.domain.draft.service;

import com.ktb.community.domain.draft.dto.DraftAutosaveResponseDto;
import com.ktb.community.domain.draft.dto.DraftRequestDto;
import com.ktb.community.domain.draft.dto.DraftResponseDto;
import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.draft.dto.DraftPublishRequestDto;
import com.ktb.community.domain.draft.dto.DraftPublishResponseDto;
import com.ktb.community.domain.draft.entity.DraftStatus;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.draft.repository.DraftCache;
import com.ktb.community.domain.draft.repository.DraftRedisRepository;
import com.ktb.community.domain.draft.repository.DraftRedisSaveResult;
import com.ktb.community.domain.draft.repository.DraftRepository;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
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
@Transactional
public class DraftService {

    private final DraftRepository draftRepository;
    private final DraftRedisRepository draftRedisRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    private static final long MAX_POSTS_PER_MINUTE = 3L;
    private static final long POST_LIMIT_MINUTES = 1L;


    @Transactional(readOnly = true)
    public Optional<DraftResponseDto> getActiveDraft(Long userId) {
        return draftRepository
                .findByActiveOwnerId(userId)
                .map(this::resolveActiveDraftResponse);
    }

    private User getActiveUserForUpdate(Long userId) {
        return userRepository.findActiveUserForUpdate(userId)
                .orElseThrow(
                        () -> new ApiException(
                                ErrorCode.UNAUTHORIZED_USER
                        )
                );
    }

    public DraftCreateResult createDraft(Long userId, DraftRequestDto request) {
        if (request.isEmptyContent()) {
            throw new ApiException(ErrorCode.DRAFT_EMPTY_CONTENT);
        }

        User user = getActiveUserForUpdate(userId);

        Optional<Draft> existingDraft = draftRepository.findByActiveOwnerId(user.getUserId());

        if (existingDraft.isPresent()) {
            DraftResponseDto response = resolveActiveDraftResponse(existingDraft.get());

            return new DraftCreateResult(response, false);
        }

        LocalDateTime now = LocalDateTime.now();

        Draft draft = new Draft(
                user,
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage(),
                request.getContentVersion(),
                now
        );

        Draft savedDraft =
                draftRepository.saveAndFlush(draft);

        DraftCache initialCache =
                toRdbCache(savedDraft);

        saveInitialCacheAfterCommit(
                initialCache
        );

        return new DraftCreateResult(
                toResponse(
                        savedDraft,
                        initialCache
                ),
                true
        );
    }

    public void deleteDraft(Long userId, Long draftId) {
        Draft draft = getOwnedActiveDraft(userId, draftId);

        draft.delete(LocalDateTime.now());

        draftRepository.flush();

        deleteRedisAfterCommit(draft.getDraftId());
    }

    @Transactional(readOnly = true)
    public DraftAutosaveResponseDto autosaveDraft(Long userId, Long draftId, DraftRequestDto request) {
        Draft draft = getOwnedActiveDraft(userId, draftId);

        LocalDateTime requestedAt = LocalDateTime.now();

        DraftCache requestCache = toRequestCache(draft, request, requestedAt);

        DraftCache rdbFallback = toRdbCache(draft);

        DraftRedisSaveResult result = draftRedisRepository.saveIfNewer(requestCache, rdbFallback);

        return handleAutosaveResult(result);
    }

    public DraftResponseDto saveDraft(Long userId, Long draftId, DraftRequestDto request) {
        Draft draft = getOwnedActiveDraft(userId, draftId);

        LocalDateTime requestedAt = LocalDateTime.now();

        DraftCache requestCache =
                toRequestCache(
                        draft,
                        request,
                        requestedAt
                );

        DraftCache rdbFallback = toRdbCache(draft);

        DraftRedisSaveResult redisResult =
                draftRedisRepository.saveIfNewer(
                        requestCache,
                        rdbFallback
                );

        DraftCache redisCache = getSuccessfulRedisCache(redisResult);

        LocalDateTime persistedAt = LocalDateTime.now();

        draft.saveSnapshot(
                redisCache.title(),
                redisCache.postBody(),
                redisCache.postImage(),
                redisCache.contentVersion(),
                persistedAt
        );

        draftRepository.flush();

        long persistedContentVersion = draft.getContentVersion();

        removeDirtyAfterCommit(draft.getDraftId(), persistedContentVersion);

        DraftCache persistedCache = toRdbCache(draft);

        return toResponse(draft, persistedCache);
    }

    public DraftPublishResponseDto publishDraft(Long userId, Long draftId, DraftPublishRequestDto request) {
        User user = userRepository.getReferenceById(userId);

        Draft draft = getPublishableDraft(userId, draftId);

        validatePostCreationRate(userId);

        LocalDateTime requestedAt = LocalDateTime.now();

        DraftCache publishRequestCache =
                toPublishRequestCache(
                        draft,
                        request,
                        requestedAt
                );

        DraftCache rdbFallback = toRdbCache(draft);

        DraftRedisSaveResult redisResult =
                draftRedisRepository.saveIfNewer(
                        publishRequestCache,
                        rdbFallback
                );

        DraftCache finalCache = getSuccessfulRedisCache(redisResult);

        Post savedPost = createPostFromDraft(user, finalCache);

        LocalDateTime publishedAt = LocalDateTime.now();

        draft.publish(
                finalCache.title(),
                finalCache.postBody(),
                finalCache.postImage(),
                finalCache.contentVersion(),
                savedPost.getPostId(),
                publishedAt
        );

        draftRepository.flush();

        deleteRedisAfterCommit(draft.getDraftId());

        return toPublishResponse(savedPost);
    }





    private void validatePostCreationRate(Long userId) {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(POST_LIMIT_MINUTES);

        long recentPostCount = postRepository.countByUserIdAndCreatedAtAfter(userId, oneMinuteAgo);

        if (recentPostCount >= MAX_POSTS_PER_MINUTE) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private Draft getPublishableDraft(Long userId, Long draftId) {
        Draft draft = draftRepository
                        .findByDraftIdAndUserId(draftId, userId)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.DRAFT_NOT_FOUND)
                        );

        if (draft.getStatus() == DraftStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.DRAFT_ALREADY_PUBLISHED);
        }

        if (!draft.isActive()) {
            throw new ApiException(ErrorCode.DRAFT_NOT_FOUND);
        }

        return draft;
    }

    private DraftCache toPublishRequestCache(Draft draft, DraftPublishRequestDto request, LocalDateTime requestedAt) {
        return new DraftCache(
                draft.getDraftId(),
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage(),
                request.getContentVersion(),
                requestedAt
        );
    }

    private Post createPostFromDraft(User user, DraftCache finalCache) {
        Post post = new Post(
                user,
                finalCache.title(),
                finalCache.postBody(),
                finalCache.postImage()
        );

        return postRepository.saveAndFlush(post);
    }

    private DraftPublishResponseDto toPublishResponse(Post post) {
        User user = post.getUser();

        return new DraftPublishResponseDto(
                post.getPostId(),
                post.getTitle(),
                post.getPostBody(),
                post.getPostImage(),
                user.getUserId(),
                user.getNickname(),
                user.getProfileImage(),
                post.getCreatedAt()
        );
    }

    private void saveInitialCacheAfterCommit(DraftCache cache) {
        registerAfterCommit(
                () -> {
                    try {
                        draftRedisRepository.saveInitial(
                                cache
                        );
                    } catch (RuntimeException e) {
                        log.warn(
                                "Failed to save initial Redis draft. draftId={}",
                                cache.draftId(),
                                e
                        );
                    }
                }
        );
    }

    private void registerAfterCommit(Runnable action) {
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

    private void deleteRedisAfterCommit(Long draftId) {
        registerAfterCommit(
                () -> {
                    try {
                        draftRedisRepository.deleteAll(
                                draftId
                        );
                    } catch (RuntimeException e) {
                        log.warn(
                                "Failed to delete Redis draft after RDB deletion. draftId={}",
                                draftId,
                                e
                        );
                    }
                }
        );
    }

    private DraftCache toRdbCache(Draft draft) {
        return new DraftCache(
                draft.getDraftId(),
                draft.getTitle(),
                draft.getPostBody(),
                draft.getPostImage(),
                draft.getContentVersion(),
                draft.getRdbSavedAt()
        );
    }

    private void validateActiveDraft(Draft draft) {
        if (!draft.isActive()) {
            throw new ApiException(
                    ErrorCode.DRAFT_NOT_FOUND
            );
        }
    }

    private DraftResponseDto toResponse(Draft draft, DraftCache selectedCache) {
        return new DraftResponseDto(
                draft.getDraftId(),
                selectedCache.title(),
                selectedCache.postBody(),
                selectedCache.postImage(),
                draft.getStatus(),
                selectedCache.contentVersion(),
                selectedCache.updatedAt(),
                draft.getRdbSavedAt()
        );
    }

    private DraftResponseDto resolveActiveDraftResponse(Draft draft) {
        validateActiveDraft(draft);

        DraftCache rdbCache = toRdbCache(draft);

        Optional<DraftCache> redisCacheOptional =
                draftRedisRepository.findById(draft.getDraftId());

        if (redisCacheOptional.isEmpty()) {
            restoreRedisFromRdb(rdbCache);

            return toResponse(draft, rdbCache);
        }

        DraftCache redisCache = redisCacheOptional.get();

        if (redisCache.contentVersion() > rdbCache.contentVersion()) {
            return toResponse(draft, redisCache);
        }

        if (redisCache.contentVersion() < rdbCache.contentVersion()) {
            replaceRedisWithRdb(rdbCache);

            return toResponse(draft, rdbCache);
        }

        if (!redisCache.hasSameContent(rdbCache)) {
            throw new ApiException(ErrorCode.DRAFT_CONTENT_CONFLICT);
        }

        DraftCache latestTimestampCache =
                redisCache.updatedAt()
                        .isAfter(rdbCache.updatedAt())
                        ? redisCache
                        : rdbCache;

        return toResponse(draft, latestTimestampCache);
    }

    private void restoreRedisFromRdb(DraftCache rdbCache) {
        try {
            draftRedisRepository.saveInitial(rdbCache);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to restore Redis draft from RDB. draftId={}",
                    rdbCache.draftId(),
                    e
            );
        }
    }

    private void replaceRedisWithRdb(DraftCache rdbCache) {
        try {
            draftRedisRepository.deleteDraft(rdbCache.draftId());
            draftRedisRepository.saveInitial(rdbCache);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to replace stale Redis draft. draftId={}",
                    rdbCache.draftId(),
                    e
            );
        }
    }

    private Draft getOwnedActiveDraft(Long userId, Long draftId) {
        return draftRepository
                .findByDraftIdAndUserId(draftId, userId)
                .filter(Draft::isActive)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.DRAFT_NOT_FOUND)
                );
    }

    private DraftCache toRequestCache(Draft draft, DraftRequestDto request, LocalDateTime requestedAt) {
        return new DraftCache(
                draft.getDraftId(),
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage(),
                request.getContentVersion(),
                requestedAt
        );
    }

    private DraftAutosaveResponseDto toAutosaveResponse(DraftCache cache) {
        return new DraftAutosaveResponseDto(
                cache.draftId(),
                cache.contentVersion(),
                cache.updatedAt()
        );
    }

    private DraftAutosaveResponseDto handleAutosaveResult(DraftRedisSaveResult result) {
        DraftCache cache = getSuccessfulRedisCache(result);

        return toAutosaveResponse(cache);
    }

    private DraftCache getSuccessfulRedisCache(
            DraftRedisSaveResult result
    ) {
        return switch (result.status()) {
            case SAVED, IDEMPOTENT ->
                    result.cache();

            case VERSION_CONFLICT ->
                    throw new ApiException(
                            ErrorCode.DRAFT_VERSION_CONFLICT
                    );

            case CONTENT_CONFLICT ->
                    throw new ApiException(
                            ErrorCode.DRAFT_CONTENT_CONFLICT
                    );
        };
    }

    private void removeDirtyAfterCommit(
            Long draftId,
            long rdbContentVersion
    ) {
        registerAfterCommit(
                () -> {
                    try {
                        boolean removed =
                                draftRedisRepository
                                        .removeDirtyIfVersionMatches(
                                                draftId,
                                                rdbContentVersion
                                        );

                        if (!removed) {
                            log.debug(
                                    "Draft dirty entry retained because Redis has a newer version. draftId={}, rdbContentVersion={}",
                                    draftId,
                                    rdbContentVersion
                            );
                        }
                    } catch (RuntimeException e) {
                        log.warn(
                                "Failed to conditionally remove dirty Draft. draftId={}, rdbContentVersion={}",
                                draftId,
                                rdbContentVersion,
                                e
                        );
                    }
                }
        );
    }
}
