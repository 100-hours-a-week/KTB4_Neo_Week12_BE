package com.ktb.community.domain.post.entity;

import com.ktb.community.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    void editedAtChangesOnlyWhenMainPostDataIsUpdated() {
        User user = new User(
                "post-test@example.com",
                "encoded-password",
                "post-test",
                null
        );
        Post post = new Post(user, "제목", "본문", "image.png");

        assertThat(post.getEditedAt()).isNull();

        post.increaseViews();
        post.increaseLikes();
        post.increaseComments();

        assertThat(post.getEditedAt()).isNull();

        post.update("수정 제목", "수정 본문", "edited-image.png");

        assertThat(post.isEdited()).isTrue();
        assertThat(post.getEditedAt()).isNotNull();
    }
}
