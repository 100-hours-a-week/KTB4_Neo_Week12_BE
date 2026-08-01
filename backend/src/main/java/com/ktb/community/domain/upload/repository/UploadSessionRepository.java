package com.ktb.community.domain.upload.repository;

import com.ktb.community.domain.upload.model.UploadPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UploadSessionRepository {

    private static final String KEY_PREFIX = "upload:session:";
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<Long> CHANGE_STATUS_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('HGET', KEYS[1], 'status')
                    if current == ARGV[1] then
                        redis.call('HSET', KEYS[1], 'status', ARGV[2])
                        return 1
                    end
                    return 0
                    """, Long.class);

    @Value("${aws.s3.upload-session-ttl-seconds}")
    private long sessionTtlSeconds;

    public void save(UploadSession session) {
        String key = key(session.uploadId());
        redisTemplate.opsForHash().putAll(key, Map.of(
                "purpose", session.purpose().name(),
                "owner", normalize(session.owner()),
                "temporaryKey", session.temporaryKey(),
                "declaredContentType", session.declaredContentType(),
                "declaredFileSize", Long.toString(session.declaredFileSize()),
                "status", session.status(),
                "finalImageUrl", normalize(session.finalImageUrl())
        ));
        redisTemplate.expire(key, Duration.ofSeconds(sessionTtlSeconds));
    }

    public Optional<UploadSession> findById(String uploadId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(uploadId));
        if (values.isEmpty()) return Optional.empty();

        return Optional.of(new UploadSession(
                uploadId,
                UploadPurpose.valueOf(value(values, "purpose")),
                emptyToNull(value(values, "owner")),
                value(values, "temporaryKey"),
                value(values, "declaredContentType"),
                Long.parseLong(value(values, "declaredFileSize")),
                value(values, "status"),
                emptyToNull(value(values, "finalImageUrl"))
        ));
    }

    public void markVerified(String uploadId, String finalImageUrl) {
        String key = key(uploadId);
        redisTemplate.opsForHash().put(key, "status", "VERIFIED");
        redisTemplate.opsForHash().put(key, "finalImageUrl", finalImageUrl);
    }

    public boolean changeStatus(String uploadId, String expectedStatus, String nextStatus) {
        Long result = redisTemplate.execute(
                CHANGE_STATUS_SCRIPT,
                List.of(key(uploadId)),
                expectedStatus,
                nextStatus
        );
        return Long.valueOf(1L).equals(result);
    }

    public void delete(String uploadId) {
        redisTemplate.delete(key(uploadId));
    }

    private String key(String uploadId) {
        return KEY_PREFIX + uploadId;
    }

    private String value(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? "" : value.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
