package com.ktb.community.domain.draft.entity;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.entity.BaseTimeEntity;
import static com.ktb.community.domain.draft.support.DraftContentNormalizer.isEmpty;
import static com.ktb.community.domain.draft.support.DraftContentNormalizer.normalizeImage;
import static com.ktb.community.domain.draft.support.DraftContentNormalizer.normalizeText;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "drafts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_drafts_active_owner",
                        columnNames = "activeOwnerId"
                )
        }
)
public class Draft extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draftId")
    private Long draftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @Column(name = "activeOwnerId")
    private Long activeOwnerId;

    @Column(name = "publishedPostId")
    private Long publishedPostId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "postBody", columnDefinition = "TEXT")
    private String postBody;

    @Column(name = "postImage", length = 500)
    private String postImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "contentVersion", nullable = false)
    private long contentVersion;

    @Version
    @Column(name = "entityVersion")
    private Long entityVersion;


    @Column(name = "rdbSavedAt", nullable = false)
    private LocalDateTime rdbSavedAt;

    @Column(name = "publishedAt")
    private LocalDateTime publishedAt;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    public Draft(User user, String title, String postBody, String postImage, long contentVersion, LocalDateTime rdbSavedAt) {
        this.user = user;
        this.activeOwnerId = user.getUserId();
        this.title = normalizeText(title);
        this.postBody = normalizeText(postBody);
        this.postImage = normalizeImage(postImage);
        this.status = DraftStatus.ACTIVE;
        this.contentVersion = contentVersion;
        this.rdbSavedAt = rdbSavedAt;
    }

    public void saveSnapshot(
            String title,
            String postBody,
            String postImage,
            long contentVersion,
            LocalDateTime savedAt
    ) {
        ensureActive();
        this.title = normalizeText(title);
        this.postBody = normalizeText(postBody);
        this.postImage = normalizeImage(postImage);
        this.contentVersion = contentVersion;
        this.rdbSavedAt = savedAt;
    }

    public void publish(
            String title,
            String postBody,
            String postImage,
            long contentVersion,
            Long publishedPostId,
            LocalDateTime publishedAt
    ) {
        saveSnapshot(
                title,
                postBody,
                postImage,
                contentVersion,
                publishedAt
        );

        this.status = DraftStatus.PUBLISHED;
        this.publishedPostId = publishedPostId;
        this.publishedAt = publishedAt;
        this.activeOwnerId = null;
    }

    public void delete(LocalDateTime deletedAt) {
        ensureActive();
        this.status = DraftStatus.DELETED;
        this.deletedAt = deletedAt;
        this.activeOwnerId = null;
    }

    public boolean isActive() {
        return status == DraftStatus.ACTIVE;
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new IllegalStateException(
                    "Draft is not active"
            );
        }
    }

    public boolean isEmptyContent() {
        return isEmpty(
                title,
                postBody,
                postImage
        );
    }
}
