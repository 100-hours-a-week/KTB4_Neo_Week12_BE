-- 조건부 dirty 제거를 위한 스크립트
-- 1분마다 실행되는 Redis -> RDB 동기화 과정과 명시적 임시 저장 직후에 사용

local draftKey = KEYS[1]
local dirtyKey = KEYS[2]

local draftId = ARGV[1]
local rdbContentVersion = tonumber(ARGV[2])

local FIELD_CONTENT_VERSION = "contentVersion"

local draftExists =
    redis.call("EXISTS", draftKey) == 1

-- Redis Draft가 존재하지 않는 경우
-- (Redis Draft가 TTL이 만료됐지만 dirty만 존재하는 경우)
-- 예를 들어 draft:15 없는데, draft:dirty에 draftId 15가 존재
-- 동기화할 본문 없으므로 고아 dirty 제거
if not draftExists then
    redis.call(
        "ZREM",
        dirtyKey,
        draftId
    )

    return 1
end

local redisContentVersion = tonumber(
    redis.call(
        "HGET",
        draftKey,
        FIELD_CONTENT_VERSION
    )
)

-- Redis -> RDB 동기화 완료한 후에 두 저장소의 Draft 버전이 같다면 추가적인 갱신 없으므로 dirty 제거
if redisContentVersion
        == rdbContentVersion then
    redis.call(
        "ZREM",
        dirtyKey,
        draftId
    )

    return 1
end

-- Redis가 RDB보다 오래된 비정상 상태
if redisContentVersion
        < rdbContentVersion then
    redis.call(
        "DEL",
        draftKey
    )

    redis.call(
        "ZREM",
        dirtyKey,
        draftId
    )

    return 1
end

-- Redis가 RDB보다 최신인 경우
-- 아직 RDB에 반영할 내용이 있으므로 dirty 유지

return 0
