package com.ktb.community.domain.post.service;

import com.ktb.community.domain.comment.repository.CommentRepository;
import com.ktb.community.domain.post.dto.LikeResponseDto;
import com.ktb.community.domain.post.dto.PostDetailResponseDto;
import com.ktb.community.domain.post.dto.PostListResponseDto;
import com.ktb.community.domain.post.dto.PostRequestDto;
import com.ktb.community.domain.post.dto.PostUpdateResponseDto;
import com.ktb.community.domain.post.dto.ReportRequestDto;
import com.ktb.community.domain.post.dto.ReportResponseDto;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.entity.PostEditHistory;
import com.ktb.community.domain.post.entity.PostLike;
import com.ktb.community.domain.post.entity.PostReport;
import com.ktb.community.domain.post.entity.PostView;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.domain.post.repository.PostEditHistoryRepository;
import com.ktb.community.domain.post.repository.PostLikeRepository;
import com.ktb.community.domain.post.repository.PostReportRepository;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.post.repository.PostViewRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private static final long ONE_DAY_HOURS = 24L;
    private static final long BLIND_REPORT_THRESHOLD = 5L;

    private final PostRepository postRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final PostViewRepository postViewRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;


    @Transactional(readOnly = true)
    public Page<PostListResponseDto> getPostList(Long userId, Pageable pageable) {

        Page<Post> postPage = postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);

        List<Long> postIds = postPage.getContent().stream()
                .map(Post::getPostId)
                .toList();

        if(postIds.isEmpty()) {
            return postPage.map(
                    post -> new PostListResponseDto(
                            post,
                            false,
                            false
                    )
            );
        }

        Set<Long> likedPostIds = new HashSet<>(
                postLikeRepository.findLikedPostIds(
                        userId,
                        postIds
                )
        );

        Set<Long> commentedPostIds = new HashSet<>(
                commentRepository.findCommentedPostIds(
                        userId,
                        postIds
                )
        );

        return postPage.map(
                post -> new PostListResponseDto(
                        post,
                        likedPostIds.contains(post.getPostId()),
                        commentedPostIds.contains(post.getPostId())
                )
        );
    }

    public PostDetailResponseDto getPostDetail(Long userId, Long postId) {
        Post post = getPost(postId);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        boolean isViewCounted = increaseViewIfNeeded(userId, post);
        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);

        return new PostDetailResponseDto(
                post,
                isLiked,
                isViewCounted,
                post.isBlinded()
        );
    }

    public PostUpdateResponseDto updatePost(Long userId, Long postId, PostRequestDto request) {
        Post post = getPost(postId);

        validatePostOwner(userId, post);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        boolean sameTitle = post.getTitle().equals(request.getTitle());
        boolean sameBody = post.getPostBody().equals(request.getPostBody());
        boolean sameImage = Objects.equals(post.getPostImage(), request.getPostImage());

        if (sameTitle && sameBody && sameImage) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        int nextRevisionNo = (int) postEditHistoryRepository.countByPostId(post.getPostId()) + 1;
        postEditHistoryRepository.save(new PostEditHistory(post, userId, nextRevisionNo));

        post.update(request.getTitle(), request.getPostBody(), request.getPostImage());

        return new PostUpdateResponseDto(
                post.getPostId(),
                true,
                post.getEditedAt()
        );
    }

    public void deletePost(Long userId, Long postId) {
        Post post = getPost(postId);

        validatePostOwner(userId, post);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        post.delete();
    }

    public LikeResponseDto likePost(Long userId, Long postId) {
        Post post = getActivePost(postId);

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        try {
            postLikeRepository.saveAndFlush(new PostLike(post, userRepository.getReferenceById(userId)));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        int updatedRows = postRepository.increaseLikes(post.getPostId());

        if(updatedRows != 1) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        int likeCount = postRepository.findLikeCount(post.getPostId());

        return new LikeResponseDto(
                post.getPostId(),
                true,
                likeCount
        );
    }

    public LikeResponseDto unlikePost(Long userId, Long postId) {
        Post post = getActivePost(postId);

        Long activePostId = post.getPostId();

        int deletedRows = postLikeRepository.deleteByPostIdAndUserId(
                activePostId,
                userId
        );

        if (deletedRows != 1) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        int updatedRows = postRepository.decreaseLikes(activePostId);

        if (updatedRows != 1) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        int likeCount = postRepository.findLikeCount(activePostId);

        return new LikeResponseDto(
                activePostId,
                false,
                likeCount
        );
    }

    public ReportResponseDto reportPost(Long userId, Long postId, ReportRequestDto request) {
        Post post = getActivePost(postId);

        if (postReportRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ApiException(ErrorCode.ALREADY_REPORTED);
        }

        PostReport postReport = new PostReport(post, userRepository.getReferenceById(userId), request.getReportType(), request.getReason());
        postReportRepository.save(postReport);

        long reportCount = postReportRepository.countByPost(post);
        if (reportCount >= BLIND_REPORT_THRESHOLD && !post.isBlinded()) {
            post.blind();
        }

        return new ReportResponseDto(post.getPostId(), (int) reportCount, post.isBlinded());
    }

    private boolean increaseViewIfNeeded(Long userId, Post post) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(ONE_DAY_HOURS);

        return postViewRepository.findByPostIdAndUserId(post.getPostId(), userId)
                .map(postView -> {
                    if (postView.getLastViewedAt().isBefore(oneDayAgo)) {
                        postView.updateLastViewedAt();

                        int updatedRows = postRepository.increaseViews(post.getPostId());
                        if (updatedRows != 1) {
                            throw new ApiException(ErrorCode.POST_NOT_FOUND);
                        }

                        return true;
                    }

                    return false;
                })
                .orElseGet(() -> {
                    PostView postView = new PostView(post, userRepository.getReferenceById(userId));
                    postViewRepository.save(postView);

                    int updatedRows = postRepository.increaseViews(post.getPostId());
                    if(updatedRows != 1) {
                        throw new ApiException(ErrorCode.POST_NOT_FOUND);
                    }

                    return true;
                });
    }

    private Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    private Post getActivePost(Long postId) {
        Post post = getPost(postId);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    private void validatePostOwner(Long userId, Post post) {
        if (!post.getUser().getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

}
