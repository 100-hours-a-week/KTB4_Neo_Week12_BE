package com.ktb.community.domain.post.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.post.dto.LikeResponseDto;
import com.ktb.community.domain.post.dto.PostCreateResponseDto;
import com.ktb.community.domain.post.dto.PostDetailResponseDto;
import com.ktb.community.domain.post.dto.PostListResponseDto;
import com.ktb.community.domain.post.dto.PostRequestDto;
import com.ktb.community.domain.post.dto.PostUpdateResponseDto;
import com.ktb.community.domain.post.dto.ReportRequestDto;
import com.ktb.community.domain.post.dto.ReportResponseDto;
import com.ktb.community.domain.post.service.PostService;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponseDto>> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PostRequestDto request
    ) {
        PostCreateResponseDto response = postService.createPost(userDetails.getUsername(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("create_post_success", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostListResponseDto>>> getPostList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size
    ) {
        int pageNumber = parseRequestParameter(page, 0, 0);
        int pageSize = parseRequestParameter(size, 20, 1);
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<PostListResponseDto> response = postService.getPostList(userDetails.getUsername(), pageable);

        return ResponseEntity.ok(
                new ApiResponse<>("get_posts_success", response)
        );
    }

    private int parseRequestParameter(String value, int defaultValue, int minimumValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue < minimumValue) {
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponseDto>> getPostDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId
    ) {
        PostDetailResponseDto response = postService.getPostDetail(userDetails.getUsername(), postId);

        return ResponseEntity.ok(
                new ApiResponse<>("get_post_detail_success", response)
        );
    }

    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostUpdateResponseDto>> updatePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody PostRequestDto request
    ) {
        PostUpdateResponseDto response = postService.updatePost(userDetails.getUsername(), postId, request);

        return ResponseEntity.ok(
                new ApiResponse<>("update_post_success", response)
        );
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Boolean>> deletePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId
    ) {
        postService.deletePost(userDetails.getUsername(), postId);

        return ResponseEntity.ok(
                new ApiResponse<>("delete_post_success", true)
        );
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponseDto>> likePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId
    ) {
        LikeResponseDto response = postService.likePost(userDetails.getUsername(), postId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("like_post_success", response));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponseDto>> unlikePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId
    ) {
        LikeResponseDto response = postService.unlikePost(userDetails.getUsername(), postId);

        return ResponseEntity.ok(
                new ApiResponse<>("unlike_post_success", response)
        );
    }

    @PostMapping("/{postId}/reports")
    public ResponseEntity<ApiResponse<ReportResponseDto>> reportPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody ReportRequestDto request
    ) {
        ReportResponseDto response = postService.reportPost(userDetails.getUsername(), postId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("report_post_success", response));
    }
}
