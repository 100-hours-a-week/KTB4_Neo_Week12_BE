package com.ktb.community.domain.comment.service;

import com.ktb.community.domain.comment.dto.CommentListResponseDto;
import com.ktb.community.domain.comment.dto.CommentRequestDto;
import com.ktb.community.domain.comment.dto.CommentResponseDto;
import com.ktb.community.domain.comment.dto.CommentUpdateResponseDto;
import com.ktb.community.domain.comment.entity.Comment;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.domain.comment.repository.CommentRepository;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentResponseDto createComment(Long userId, Long postId, CommentRequestDto request) {
        User user = userRepository.getReferenceById(userId);
        Post post = getActivePost(postId);

        Comment comment = new Comment(
                post,
                user,
                null,
                request.getCommentBody()
        );

        Comment savedComment = commentRepository.save(comment);

        int updatedRows = postRepository.increaseComments(post.getPostId());

        if(updatedRows != 1){
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        return toCommentResponse(savedComment);
    }

    public CommentResponseDto createReply(Long userId, Long parentCommentId, CommentRequestDto request) {
        User user = userRepository.getReferenceById(userId);

        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PARENT_COMMENT_NOT_FOUND));

        Post post = parentComment.getPost();

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        if(parentComment.isDeleted()) {
            throw new ApiException(ErrorCode.ALREADY_DELETED);
        }

        if (parentComment.getParentComment() != null) {
            throw new ApiException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        }

        Comment reply = new Comment(
                post,
                user,
                parentComment,
                request.getCommentBody()
        );

        Comment savedReply = commentRepository.save(reply);

        int updatedRows = postRepository.increaseComments(post.getPostId());

        if(updatedRows != 1) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        return toCommentResponse(savedReply);
    }

    @Transactional(readOnly = true)
    public List<CommentListResponseDto> getCommentsList(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        List<Comment> comments = commentRepository.findCommentsWithUserByPost(post);

        Map<Long, List<CommentListResponseDto.ReplyResponseDto>> repliesByParentId =
                comments.stream()
                        .filter(comment -> comment.getParentComment() != null)
                        .collect(Collectors.groupingBy(
                                comment -> comment.getParentComment().getCommentId(),
                                Collectors.mapping(
                                        reply -> new CommentListResponseDto.ReplyResponseDto(
                                                reply.getCommentId(),
                                                reply.getUser().getUserId(),
                                                reply.isDeleted() || reply.getUser().isDeleted()
                                                        ? "알 수 없음"
                                                        : reply.getUser().getNickname(),
                                                reply.isDeleted() || reply.getUser().isDeleted()
                                                        ? null
                                                        : reply.getUser().getProfileImage(),
                                                reply.isDeleted()
                                                        ? "삭제된 댓글입니다."
                                                        : reply.getCommentBody(),
                                                reply.isDeleted(),
                                                reply.getCreatedAt()
                                        ),
                                        Collectors.toList()
                                )
                        ));

        return comments.stream()
                .filter(comment -> comment.getParentComment() == null)
                .map(parentComment -> {
                    List<CommentListResponseDto.ReplyResponseDto> replies =
                            repliesByParentId.getOrDefault(parentComment.getCommentId(), List.of());

                    if (parentComment.isDeleted() && replies.isEmpty()) {
                        return null;
                    }

                    return new CommentListResponseDto(parentComment, replies);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public CommentUpdateResponseDto updateComment(Long userId, Long commentId, CommentRequestDto request) {
        Comment comment = getComment(commentId);

        validateCommentOwner(userId, comment);

        if (comment.isDeleted()) {
            throw new ApiException(ErrorCode.ALREADY_DELETED);
        }

        comment.update(request.getCommentBody());

        return new CommentUpdateResponseDto(
                comment.getCommentId(),
                comment.getUpdatedAt(),
                true
        );
    }

    public void deleteComment(Long userId, Long commentId) {
        Comment comment = getComment(commentId);

        validateCommentOwner(userId, comment);

        Long postId = comment.getPost().getPostId();

        int deletedRows = commentRepository.softDeleteIfActive(
                commentId,
                LocalDateTime.now()
        );

        if (deletedRows != 1) {
            throw new ApiException(ErrorCode.ALREADY_DELETED);
        }

        int updatedRows = postRepository.decreaseComments(postId);

        if (updatedRows != 1) {
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }
    }

    private Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Post getActivePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    private void validateCommentOwner(Long userId, Comment comment) {
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

    private CommentResponseDto toCommentResponse(Comment comment) {
        return new CommentResponseDto(
                comment.getCommentId(),
                comment.getParentComment() != null ? comment.getParentComment().getCommentId() : null,
                comment.getUser().getUserId(),
                comment.getUser().getNickname(),
                comment.getUser().getProfileImage(),
                comment.getCommentBody(),
                comment.getCreatedAt()
        );
    }
}
