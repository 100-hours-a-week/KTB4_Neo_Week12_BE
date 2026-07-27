package com.ktb.community.domain.post.entity;

import com.ktb.community.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "post_likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_post_likes_post_user", columnNames = {"postId", "userId"})
        }
)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "postLikeId")
    private Long postLikeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    public PostLike(Post post, User user) {
        this.post = post;
        this.user = user;
    }

}
