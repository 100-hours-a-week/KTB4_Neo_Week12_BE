package com.ktb.community.global.exception;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "invalid_input"),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, "unauthorized_user"),
    DENIED_ACCESS(HttpStatus.FORBIDDEN, "denied_access"),

    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "already_exists"),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "not_found_user"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "invalid_password"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "refresh_token_not_found"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_refresh_token"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "refresh_token_expired"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "password_mismatch"),

    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post_not_found"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "comment_not_found"),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "parent_comment_not_found"),

    CONFLICTED_STATE(HttpStatus.CONFLICT, "conflicted_state"),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "already_reported"),
    ALREADY_DELETED(HttpStatus.CONFLICT, "already_deleted"),
    REPLY_DEPTH_EXCEEDED(HttpStatus.CONFLICT, "reply_depth_exceeded"),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error"),

    DRAFT_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "draft_already_published"),
    DRAFT_EMPTY_CONTENT(HttpStatus.BAD_REQUEST,  "draft_empty_content"),
    DRAFT_VERSION_CONFLICT(HttpStatus.CONFLICT, "draft_version_conflict"),
    DRAFT_CONTENT_CONFLICT(HttpStatus.CONFLICT, "draft_content_conflict"),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "draft_not_found");

    private final HttpStatus status;
    private final String message;

}
