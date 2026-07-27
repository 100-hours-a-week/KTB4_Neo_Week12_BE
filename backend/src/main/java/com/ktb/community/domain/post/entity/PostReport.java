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
        name = "post_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_post_reports_post_user", columnNames = {"postId", "userId"})
        }
)
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reportId")
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reportType", nullable = false)
    private ReportType reportType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PostReportStatus status;

    @Column(name = "reportedAt", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "processedAt")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    public PostReport(Post post, User user, ReportType reportType, String reason) {
        this.post = post;
        this.user = user;
        this.reportType = reportType;
        this.reason = reason;
        this.status = PostReportStatus.PENDING;
        this.reportedAt = LocalDateTime.now();
    }
}
