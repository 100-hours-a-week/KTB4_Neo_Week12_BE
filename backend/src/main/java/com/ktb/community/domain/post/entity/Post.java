package com.ktb.community.domain.post.entity;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "posts")
public class Post extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "postId")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;


    @Column(name = "postBody", nullable = false, columnDefinition = "TEXT")
    private String postBody;

    @Column(name = "postImage", length = 500)
    private String postImage;

    @Column(name = "likes", nullable = false)
    private int likes = 0;

    @Column(name = "views", nullable = false)
    private int views = 0;

    @Column(name = "comments", nullable = false)
    private int comments = 0;

    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "blinded", nullable = false)
    private boolean blinded = false;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    @Column(name = "editedAt")
    private LocalDateTime editedAt;

    public Post(User user, String title, String postBody, String postImage) {
        this.user = user;
        this.title = title;
        this.postBody = postBody;
        this.postImage = postImage;
        this.deletedAt = null;
    }

    public void update(String title, String postBody, String postImage) {
        this.title = title;
        this.postBody = postBody;
        this.postImage = postImage;
        this.edited = true;
        this.editedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void increaseViews() {
        this.views++;
    }

    public void increaseLikes() {
        this.likes++;
    }

    public void decreaseLikes() {
        this.likes--;
    }

    public void increaseComments() {
        this.comments++;
    }


    public void decreaseComments() {
        if (this.comments > 0) {
            this.comments--;
        }
    }

    public void blind() {
        this.blinded = true;
    }
}
