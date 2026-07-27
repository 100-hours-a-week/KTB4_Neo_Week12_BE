package com.ktb.community.domain.post.entity;

import com.ktb.community.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "post_edit_history",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_post_edit_history_post_revision",
                        columnNames = {"postId", "revisionNo"}
                )
        }
)
public class PostEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historyId")
    private Long historyId;

    @Column(name = "postId", nullable = false)
    private Long postId;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "postBody", nullable = false, columnDefinition = "TEXT")
    private String postBody;

    @Column(name = "postImage", length = 500)
    private String postImage;

    @Column(name = "revisionNo", nullable = false)
    private int revisionNo;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    public PostEditHistory(Post post, User user, int revisionNo) {
        this.postId = post.getPostId();
        this.userId = user.getUserId();
        this.title = post.getTitle();
        this.postBody = post.getPostBody();
        this.postImage = post.getPostImage();
        this.revisionNo = revisionNo;
        this.createdAt = LocalDateTime.now();
    }
}