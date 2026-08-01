-- 원자적 자동 저장을 위한 Lua Script
-- 사용자가 게시글 작성 폼에서 공백이 아닌 입력을 한 후, 2초동안 아무런 입력 없다면 자동 저장이 실행된다.

local draftKey = KEYS[1]
local dirtyKey = KEYS[2]

local draftId = ARGV[1]

local requestTitle = ARGV[2]
local requestPostBody = ARGV[3]
local requestPostImage = ARGV[4]
local requestContentVersion = tonumber(ARGV[5])

local fallbackTitle = ARGV[6]
local fallbackPostBody = ARGV[7]
local fallbackPostImage = ARGV[8]
local fallbackContentVersion = tonumber(ARGV[9])
local fallbackUpdatedAt = ARGV[10]

local requestUpdatedAt = ARGV[11]
local ttlSeconds = tonumber(ARGV[12])
local dirtyScore = tonumber(ARGV[13])

local FIELD_DRAFT_ID = "draftId"
local FIELD_TITLE = "title"
local FIELD_POST_BODY = "postBody"
local FIELD_POST_IMAGE = "postImage"
local FIELD_CONTENT_VERSION = "contentVersion"
local FIELD_UPDATED_AT = "updatedAt"

local function result(
    status,
    title,
    postBody,
    postImage,
    contentVersion,
    updatedAt
)
    return {
        tostring(status),
        title,
        postBody,
        postImage,
        tostring(contentVersion),
        updatedAt
    }
end

local draftExists =
    redis.call("EXISTS", draftKey) == 1

local storedTitle
local storedPostBody
local storedPostImage
local storedContentVersion
local storedUpdatedAt
local usingFallback = false

if draftExists then
    storedTitle = redis.call(
        "HGET",
        draftKey,
        FIELD_TITLE
    )

    storedPostBody = redis.call(
        "HGET",
        draftKey,
        FIELD_POST_BODY
    )

    storedPostImage = redis.call(
        "HGET",
        draftKey,
        FIELD_POST_IMAGE
    )

    storedContentVersion = tonumber(
        redis.call(
            "HGET",
            draftKey,
            FIELD_CONTENT_VERSION
        )
    )

    storedUpdatedAt = redis.call(
        "HGET",
        draftKey,
        FIELD_UPDATED_AT
    )

    -- Redis가 존재하더라도 RDB fallback보다 버전이 낮은 경우
    -- RDB 데이터의 버전을 자동 저장 요청에 들어온 임시글의 버전과 비교하는 기준으로 삼는다.
    if fallbackContentVersion
            > storedContentVersion then
        storedTitle = fallbackTitle
        storedPostBody = fallbackPostBody
        storedPostImage = fallbackPostImage
        storedContentVersion =
            fallbackContentVersion
        storedUpdatedAt =
            fallbackUpdatedAt
        usingFallback = true
    end
else
    storedTitle = fallbackTitle
    storedPostBody = fallbackPostBody
    storedPostImage = fallbackPostImage
    storedContentVersion =
        fallbackContentVersion
    storedUpdatedAt = fallbackUpdatedAt
end

if requestContentVersion
        < storedContentVersion then
    return result(
        3,  -- 요청 Draft가 저장된 Draft 보다 낮은 버전인 경우, Redis 수정 x
        storedTitle,
        storedPostBody,
        storedPostImage,
        storedContentVersion,
        storedUpdatedAt
    )
end

-- 저장된 Draft와 요청 Draft 버전 비교
if requestContentVersion
        == storedContentVersion then

    local sameTitle =
        requestTitle == storedTitle

    local samePostBody =
        requestPostBody == storedPostBody

    local samePostImage =
        requestPostImage == storedPostImage

    -- 버전이 같고, Draft 내용까지 같은 경우
    if sameTitle
            and samePostBody
            and samePostImage then

        -- Redis에 없고 RDB에 동일한 버전 + 내용의 Draft 존재하는 경우
        -- RDB 데이터를 Redis 캐시로 복구
        if not draftExists
                or usingFallback then
            redis.call(
                "HSET",
                draftKey,
                FIELD_DRAFT_ID,
                draftId,
                FIELD_TITLE,
                storedTitle,
                FIELD_POST_BODY,
                storedPostBody,
                FIELD_POST_IMAGE,
                storedPostImage,
                FIELD_CONTENT_VERSION,
                tostring(
                    storedContentVersion
                ),
                FIELD_UPDATED_AT,
                storedUpdatedAt
            )

            redis.call(
                "EXPIRE",
                draftKey,
                ttlSeconds
            )
        end

        return result(
            2,  -- 버전도 같고, 내용도 같음 -> 멱등
            storedTitle,
            storedPostBody,
            storedPostImage,
            storedContentVersion,
            storedUpdatedAt
        )
    end

    return result(
        4,  -- 버전이 같은데 내용이 다른 경우 -> CONTENT CONFLICT 충돌 발생, Redis 건들지 않아도 됨.
        storedTitle,
        storedPostBody,
        storedPostImage,
        storedContentVersion,
        storedUpdatedAt
    )
end

-- 앞의 두 조건을 통과한 경우, 즉 요청 버전이 저장 버전보다 높은 경우
-- Redis Draft Hash 캐시 갱신
redis.call(
    "HSET",
    draftKey,
    FIELD_DRAFT_ID,
    draftId,
    FIELD_TITLE,
    requestTitle,
    FIELD_POST_BODY,
    requestPostBody,
    FIELD_POST_IMAGE,
    requestPostImage,
    FIELD_CONTENT_VERSION,
    tostring(requestContentVersion),
    FIELD_UPDATED_AT,
    requestUpdatedAt
)

-- TTL 갱신
redis.call(
    "EXPIRE",
    draftKey,
    ttlSeconds
)

-- Dirty 등록
redis.call(
    "ZADD",
    dirtyKey,
    dirtyScore,
    draftId
)

return result(
    1,  -- 성공적으로 원자적 자동 저장 완료
    requestTitle,
    requestPostBody,
    requestPostImage,
    requestContentVersion,
    requestUpdatedAt
)
