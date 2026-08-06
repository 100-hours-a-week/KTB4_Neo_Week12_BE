package com.ktb.community.domain.post.service;

import com.ktb.community.domain.comment.repository.CommentRepository;
import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.post.repository.PostEditHistoryRepository;
import com.ktb.community.domain.post.repository.PostLikeRepository;
import com.ktb.community.domain.post.repository.PostReportRepository;
import com.ktb.community.domain.post.repository.PostRepository;
import com.ktb.community.domain.post.repository.PostViewRepository;
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

class PostServiceAuthorizationTest {

    private final PostRepository postRepository = mock(PostRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostService service = new PostService(
            postRepository,
            mock(PostEditHistoryRepository.class),
            mock(PostViewRepository.class),
            mock(PostLikeRepository.class),
            mock(PostReportRepository.class),
            userRepository,
            mock(CommentRepository.class)
    );

    @Test
    void blocksUpdatingAnotherUsersPostWithoutReloadingAuthenticatedUser() {
        User owner = new User("owner@example.com", "password", "owner", null);
        ReflectionTestUtils.setField(owner, "userId", 2L);
        Post post = new Post(owner, "title", "body", null);
        ReflectionTestUtils.setField(post, "postId", 10L);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(1L, 10L, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DENIED_ACCESS);

        verify(userRepository, never()).findByUserIdAndDeletedFalse(1L);
        verify(userRepository, never()).findByEmailAndDeletedFalse("owner@example.com");
    }
}
