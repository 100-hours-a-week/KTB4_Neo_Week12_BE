package com.ktb.community.domain.draft.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static com.ktb.community.domain.draft.support.DraftContentNormalizer.normalizeImage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class DraftRedisRepository {

    private static final String DRAFT_KEY_PREFIX = "draft:";
    private static final String DIRTY_KEY = "draft:dirty";
    private static final String FIELD_DRAFT_ID = "draftId";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_POST_BODY = "postBody";
    private static final String FIELD_POST_IMAGE = "postImage";
    private static final String FIELD_CONTENT_VERSION = "contentVersion";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOperations;
    private final Duration redisTtl;

    private final DefaultRedisScript<List> autosaveScript;
    private final DefaultRedisScript<Long> removeDirtyScript;


    public DraftRedisRepository(
            StringRedisTemplate redisTemplate,
            @Value("${draft.redis-ttl}")
            Duration redisTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.hashOperations =
                redisTemplate.opsForHash();
        this.redisTtl = redisTtl;

        this.autosaveScript = createAutosaveScript();

        this.removeDirtyScript =
                createRemoveDirtyScript();
    }

    public Optional<DraftCache> findById(Long draftId) {
        String key = draftKey(draftId);

        Map<String, String> entries = hashOperations.entries(key);

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                toDraftCache(draftId, entries)
        );
    }

    public boolean exists(Long draftId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(
                        draftKey(draftId)
                )
        );
    }

    public void saveInitial(DraftCache cache) {
        String key = draftKey(cache.draftId());

        redisTemplate
                .opsForHash()
                .putAll(
                        key,
                        toHash(cache)
                );

        Boolean expirationApplied =
                redisTemplate.expire(
                        key,
                        redisTtl
                );

        if (!Boolean.TRUE.equals(expirationApplied)) {
            redisTemplate.delete(key);

            throw new IllegalStateException("Failed to apply TTL to draft cache");
        }
    }

    public List<Long> findDirtyDraftIds(long maxScore, int limit) {
        if (limit < 1) {
            return List.of();
        }

        Set<String> members =
                redisTemplate
                        .opsForZSet()
                        .rangeByScore(
                                DIRTY_KEY,
                                0,
                                maxScore,
                                0,
                                limit
                        );

        if (members == null || members.isEmpty()) {
            return List.of();
        }

        return members.stream()
                .map(this::parseDraftId)
                .toList();
    }

    public void removeDirty(Long draftId) {
        redisTemplate
                .opsForZSet()
                .remove(
                        DIRTY_KEY,
                        draftId.toString()
                );
    }

    public void deleteDraft(Long draftId) {
        redisTemplate.delete(
                draftKey(draftId)
        );
    }

    public void deleteAll(Long draftId) {
        deleteDraft(draftId);
        removeDirty(draftId);
    }

    private Map<String, String> toHash(
            DraftCache cache
    ) {
        Map<String, String> values = new LinkedHashMap<>();

        values.put(FIELD_DRAFT_ID, cache.draftId().toString());
        values.put(FIELD_TITLE, cache.title());
        values.put(FIELD_POST_BODY, cache.postBody());
        values.put(FIELD_POST_IMAGE, encodeImage(cache.postImage()));
        values.put(FIELD_CONTENT_VERSION, Long.toString(cache.contentVersion()));
        values.put(FIELD_UPDATED_AT, cache.updatedAt().toString());

        return values;
    }

    private DraftCache toDraftCache(
            Long expectedDraftId,
            Map<String, String> entries
    ) {
        Long storedDraftId = parseLong(requireField(entries, FIELD_DRAFT_ID), FIELD_DRAFT_ID);

        if (!expectedDraftId.equals(storedDraftId)) {
            throw new IllegalStateException(
                    "Draft cache ID does not match Redis key"
            );
        }

        long version = parseLong(
                requireField(entries, FIELD_CONTENT_VERSION),
                FIELD_CONTENT_VERSION
        );

        LocalDateTime updatedAt =
                parseUpdatedAt(requireField(entries, FIELD_UPDATED_AT));

        return new DraftCache(
                storedDraftId,
                requireField(
                        entries,
                        FIELD_TITLE
                ),
                requireField(
                        entries,
                        FIELD_POST_BODY
                ),
                normalizeImage(
                        requireField(
                                entries,
                                FIELD_POST_IMAGE
                        )
                ),
                version,
                updatedAt
        );
    }

    private String requireField(Map<String, String> entries, String field) {
        Object value = entries.get(field);

        if (value == null) {
            throw new IllegalStateException(
                    "Missing Redis draft field: "
                            + field
            );
        }

        return value.toString();
    }

    private long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid numeric Redis draft field: "
                            + field,
                    e
            );
        }
    }

    private LocalDateTime parseUpdatedAt(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid Redis draft updatedAt",
                    e
            );
        }
    }

    private Long parseDraftId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid draftId in dirty set",
                    e
            );
        }
    }

    private String encodeImage(String postImage) {
        return postImage == null
                ? ""
                : postImage;
    }

    private String draftKey(Long draftId) {
        if (draftId == null) {
            throw new IllegalArgumentException(
                    "draftId must not be null"
            );
        }

        return DRAFT_KEY_PREFIX + draftId;
    }

    public DraftRedisSaveResult saveIfNewer(DraftCache request, DraftCache fallback) {
        validateSameDraft(request, fallback);

        long ttlSeconds = redisTtl.toSeconds();

        if (ttlSeconds < 1) {
            throw new IllegalStateException("Draft Redis TTL must be positive");
        }

        List<?> result =
                redisTemplate.execute(
                        autosaveScript,
                        List.of(
                                draftKey(
                                        request.draftId()
                                ),
                                DIRTY_KEY
                        ),
                        request.draftId()
                                .toString(),
                        request.title(),
                        request.postBody(),
                        encodeImage(
                                request.postImage()
                        ),
                        Long.toString(
                                request.contentVersion()
                        ),
                        fallback.title(),
                        fallback.postBody(),
                        encodeImage(
                                fallback.postImage()
                        ),
                        Long.toString(
                                fallback.contentVersion()
                        ),
                        fallback.updatedAt()
                                .toString(),
                        request.updatedAt()
                                .toString(),
                        Long.toString(ttlSeconds),
                        Long.toString(
                                System.currentTimeMillis()
                        )
                );

        return toSaveResult(request.draftId(), result);
    }

    private void validateSameDraft(DraftCache request, DraftCache fallback) {
        if (!request.draftId().equals(fallback.draftId())) {
            throw new IllegalArgumentException(
                    "Request and fallback draft IDs "
                            + "must match"
            );
        }
    }

    private DraftRedisSaveResult toSaveResult(Long draftId, List<?> result) {
        if (result == null || result.size() != 6) {
            throw new IllegalStateException(
                    "Invalid autosave Lua result"
            );
        }

        DraftRedisSaveStatus status = parseSaveStatus(resultValue(result, 0));

        String title = resultValue(result, 1);

        String postBody = resultValue(result, 2);

        String postImage = normalizeImage(resultValue(result, 3));

        long contentVersion = parseLong(resultValue(result, 4), FIELD_CONTENT_VERSION);

        LocalDateTime updatedAt = parseUpdatedAt(resultValue(result, 5));

        DraftCache cache = new DraftCache(
                draftId,
                title,
                postBody,
                postImage,
                contentVersion,
                updatedAt
        );

        return new DraftRedisSaveResult(status, cache);
    }

    private String resultValue(List<?> result, int index) {
        Object value = result.get(index);

        if (value == null) {
            throw new IllegalStateException(
                    "Null value in autosave Lua result"
            );
        }

        return value.toString();
    }

    private DraftRedisSaveStatus parseSaveStatus(String value) {
        return switch (value) {
            case "1" -> DraftRedisSaveStatus.SAVED;

            case "2" -> DraftRedisSaveStatus.IDEMPOTENT;

            case "3" -> DraftRedisSaveStatus.VERSION_CONFLICT;

            case "4" -> DraftRedisSaveStatus.CONTENT_CONFLICT;

            default -> throw new IllegalStateException(
                            "Unknown autosave status: " + value
                    );
        };
    }

    public boolean removeDirtyIfVersionMatches(Long draftId, long rdbContentVersion) {
        Long result =
                redisTemplate.execute(
                        removeDirtyScript,
                        List.of(
                                draftKey(draftId),
                                DIRTY_KEY
                        ),
                        draftId.toString(),
                        Long.toString(
                                rdbContentVersion
                        )
                );

        if (result == null) {
            throw new IllegalStateException(
                    "Null remove-dirty Lua result"
            );
        }

        return result == 1L;
    }

    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> createAutosaveScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();

        script.setLocation(new ClassPathResource("redis/draft-autosave.lua"));

        script.setResultType(List.class);

        return script;
    }

    private DefaultRedisScript<Long> createRemoveDirtyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setLocation(new ClassPathResource("redis/draft-remove-dirty.lua"));

        script.setResultType(Long.class);

        return script;
    }
}
