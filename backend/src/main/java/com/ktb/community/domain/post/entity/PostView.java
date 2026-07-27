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
        name = "post_views",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_post_views_post_user", columnNames = {"postId", "userId"})
        }
)
public class PostView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "postViewId")
    private Long postViewId;

    @Column(name = "lastViewedAt", nullable = false)
    private LocalDateTime lastViewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    public PostView(Post post, User user) {
        this.post = post;
        this.user = user;
        this.lastViewedAt = LocalDateTime.now();
    }

    public void updateLastViewedAt() {
        this.lastViewedAt = LocalDateTime.now();
    }
}
