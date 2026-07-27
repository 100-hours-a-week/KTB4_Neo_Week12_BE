package com.ktb.community.domain.post.service;

import com.ktb.community.domain.comment.repository.CommentRepository;
import com.ktb.community.domain.post.dto.LikeResponseDto;
import com.ktb.community.domain.post.dto.PostCreateResponseDto;
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
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.domain.post.repository.PostEditHistoryRepository;
import com.ktb.community.domain.post.repository.PostLikeRepository;
import com.ktb.community.domain.post.repository.PostReportRepository;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.post.repository.PostViewRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private static final long POST_LIMIT_MINUTES = 1L;
    private static final long MAX_POSTS_PER_MINUTE = 3L;
    private static final long ONE_DAY_HOURS = 24L;
    private static final long BLIND_REPORT_THRESHOLD = 5L;

    private final PostRepository postRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final PostViewRepository postViewRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public PostCreateResponseDto createPost(String email, PostRequestDto request) {
        User user = getActiveUser(email);
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(POST_LIMIT_MINUTES);

        long recentPostCount = postRepository.countByUserAndCreatedAtAfter(user, oneMinuteAgo);

        if (recentPostCount >= MAX_POSTS_PER_MINUTE) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }

        Post post = new Post(
                user,
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage()
        );

        Post savedPost = postRepository.save(post);

        return new PostCreateResponseDto(
                savedPost.getPostId(),
                savedPost.getTitle(),
                savedPost.getPostBody(),
                savedPost.getPostImage(),
                savedPost.getUser().getUserId(),
                savedPost.getUser().getNickname(),
                savedPost.getUser().getProfileImage(),
                savedPost.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<PostListResponseDto> getPostList(String email, Pageable pageable) {
        User user = getActiveUser(email);

        return postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> new PostListResponseDto(
                        post,
                        postLikeRepository.existsByPostAndUser(post, user),
                        commentRepository.existsByPostAndUserAndDeletedFalse(post, user)
                ));
    }

    public PostDetailResponseDto getPostDetail(String email, Long postId) {
        User user = getActiveUser(email);
        Post post = getPost(postId);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        boolean isViewCounted = increaseViewIfNeeded(user, post);
        boolean isLiked = postLikeRepository.existsByPostAndUser(post, user);

        return new PostDetailResponseDto(
                post,
                isLiked,
                isViewCounted,
                post.isBlinded()
        );
    }

    public PostUpdateResponseDto updatePost(String email, Long postId, PostRequestDto request) {
        User user = getActiveUser(email);
        Post post = getPost(postId);

        validatePostOwner(user, post);

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
        postEditHistoryRepository.save(new PostEditHistory(post, user, nextRevisionNo));

        post.update(request.getTitle(), request.getPostBody(), request.getPostImage());

        return new PostUpdateResponseDto(
                post.getPostId(),
                true,
                post.getEditedAt()
        );
    }

    public void deletePost(String email, Long postId) {
        User user = getActiveUser(email);
        Post post = getPost(postId);

        validatePostOwner(user, post);

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        post.delete();
    }

    public LikeResponseDto likePost(String email, Long postId) {
        User user = getActiveUser(email);
        Post post = getActivePost(postId);

        if (postLikeRepository.existsByPostAndUser(post, user)) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        PostLike postLike = new PostLike(post, user);
        postLikeRepository.save(postLike);
        post.increaseLikes();

        return new LikeResponseDto(post.getPostId(), true, post.getLikes());
    }

    public LikeResponseDto unlikePost(String email, Long postId) {
        User user = getActiveUser(email);
        Post post = getActivePost(postId);

        PostLike postLike = postLikeRepository.findByPostAndUser(post, user)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICTED_STATE));

        postLikeRepository.delete(postLike);
        post.decreaseLikes();

        return new LikeResponseDto(post.getPostId(), false, post.getLikes());
    }

    public ReportResponseDto reportPost(String email, Long postId, ReportRequestDto request) {
        User user = getActiveUser(email);
        Post post = getActivePost(postId);

        if (postReportRepository.existsByPostAndUser(post, user)) {
            throw new ApiException(ErrorCode.ALREADY_REPORTED);
        }

        PostReport postReport = new PostReport(post, user, request.getReportType(), request.getReason());
        postReportRepository.save(postReport);

        long reportCount = postReportRepository.countByPost(post);
        if (reportCount >= BLIND_REPORT_THRESHOLD && !post.isBlinded()) {
            post.blind();
        }

        return new ReportResponseDto(post.getPostId(), (int) reportCount, post.isBlinded());
    }

    private boolean increaseViewIfNeeded(User user, Post post) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(ONE_DAY_HOURS);

        return postViewRepository.findByPostAndUser(post, user)
                .map(postView -> {
                    if (postView.getLastViewedAt().isBefore(oneDayAgo)) {
                        postView.updateLastViewedAt();
                        post.increaseViews();
                        return true;
                    }

                    return false;
                })
                .orElseGet(() -> {
                    PostView postView = new PostView(post, user);
                    postViewRepository.save(postView);
                    post.increaseViews();
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

    private void validatePostOwner(User user, Post post) {
        if (!post.getUser().getUserId().equals(user.getUserId())) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

    private User getActiveUser(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_USER));

        return user;
    }
}
