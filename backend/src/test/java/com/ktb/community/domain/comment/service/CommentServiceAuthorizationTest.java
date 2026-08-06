package com.ktb.community.domain.comment.service;

import com.ktb.community.domain.comment.entity.Comment;
import com.ktb.community.domain.comment.repository.CommentRepository;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommentServiceAuthorizationTest {

    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommentService service = new CommentService(
            commentRepository,
            mock(PostRepository.class),
            userRepository
    );

    @Test
    void blocksUpdatingAnotherUsersCommentWithoutReloadingAuthenticatedUser() {
        User owner = new User("owner@example.com", "password", "owner", null);
        ReflectionTestUtils.setField(owner, "userId", 2L);
        Post post = new Post(owner, "title", "body", null);
        Comment comment = new Comment(post, owner, null, "comment");
        ReflectionTestUtils.setField(comment, "commentId", 20L);
        given(commentRepository.findById(20L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.updateComment(1L, 20L, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED_ACCESS);

        verify(userRepository, never()).findByUserIdAndDeletedFalse(1L);
        verify(userRepository, never()).findByEmailAndDeletedFalse("owner@example.com");
    }
}
