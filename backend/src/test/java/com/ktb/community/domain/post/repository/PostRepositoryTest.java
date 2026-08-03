package com.ktb.community.domain.post.repository;

import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void counterUpdatesDoNotChangeEditedAt() {
        User user = userRepository.save(
                new User(
                        "post-test@example.com",
                        "encoded-password",
                        "post-test",
                        null
                )
        );

        Post post = postRepository.saveAndFlush(
                new Post(
                        user,
                        "제목",
                        "본문",
                        "image.png"
                )
        );

        Long postId = post.getPostId();

        assertThat(post.getEditedAt()).isNull();

        int viewUpdatedRows =
                postRepository.increaseViews(postId);

        int likeUpdatedRows =
                postRepository.increaseLikes(postId);

        int commentUpdatedRows =
                postRepository.increaseComments(postId);

        assertThat(viewUpdatedRows).isEqualTo(1);
        assertThat(likeUpdatedRows).isEqualTo(1);
        assertThat(commentUpdatedRows).isEqualTo(1);

        entityManager.clear();

        Post updatedPost = postRepository.findById(postId)
                .orElseThrow();

        assertThat(updatedPost.getViews()).isEqualTo(1);
        assertThat(updatedPost.getLikes()).isEqualTo(1);
        assertThat(updatedPost.getComments()).isEqualTo(1);

        assertThat(updatedPost.isEdited()).isFalse();
        assertThat(updatedPost.getEditedAt()).isNull();
    }
}