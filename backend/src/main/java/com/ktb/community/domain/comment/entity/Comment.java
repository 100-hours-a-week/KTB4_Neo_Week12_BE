package com.ktb.community.domain.comment.entity;

import com.ktb.community.domain.post.entity.Post;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "comments")
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commentId")
    private Long commentId;

    @Column(name = "commentBody", nullable = false, columnDefinition = "TEXT")
    private String commentBody;

    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentCommentId")
    private Comment parentComment;

    public Comment(Post post, User user, Comment parentComment, String commentBody) {
        this.post = post;
        this.user = user;
        this.parentComment = parentComment;
        this.commentBody = commentBody;
    }

    public void update(String commentBody) {
        this.commentBody = commentBody;
        this.edited = true;
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
