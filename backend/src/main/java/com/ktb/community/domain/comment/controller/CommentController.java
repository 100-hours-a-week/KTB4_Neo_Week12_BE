package com.ktb.community.domain.comment.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.comment.dto.CommentListResponseDto;
import com.ktb.community.domain.comment.dto.CommentRequestDto;
import com.ktb.community.domain.comment.dto.CommentResponseDto;
import com.ktb.community.domain.comment.dto.CommentUpdateResponseDto;
import com.ktb.community.domain.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponseDto>> createComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequestDto request
    ) {
        CommentResponseDto response = commentService.createComment(userDetails.getUsername(), postId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("create_comment_success", response));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<CommentResponseDto>> createReply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequestDto request
    ) {
        CommentResponseDto response = commentService.createReply(userDetails.getUsername(), commentId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("create_reply_success", response));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentListResponseDto>>> getCommentsList(
            @PathVariable Long postId
    ) {
        List<CommentListResponseDto> response = commentService.getCommentsList(postId);

        return ResponseEntity.ok(
                new ApiResponse<>("get_comments_success", response)
        );
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentUpdateResponseDto>> updateComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequestDto request
    ) {
        CommentUpdateResponseDto response = commentService.updateComment(userDetails.getUsername(), commentId, request);

        return ResponseEntity.ok(
                new ApiResponse<>("update_comment_success", response)
        );
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(userDetails.getUsername(), commentId);

        return ResponseEntity.ok(
                new ApiResponse<>("delete_comment_success", true)
        );
    }
}
