package com.ktb.community.domain.user.entity;

import com.ktb.community.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userId")
    private Long userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 255)
    private String nickname;

    @Column(name = "profileImage", length = 500)
    private String profileImage;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    public User(String email, String password, String nickname, String profileImage) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = UserRole.ROLE_USER;
    }

    /* 나중에 관리자 권환 관련 서비스 로직이 생긴다면 일반 유저에게 관리자 권한을 부여하기 위해 사용될 수 있음.
    public void userToAdmin() {
        this.role = UserRole.ROLE_ADMIN;
    }
     */


    public void update(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    public void delete() {
        this.profileImage = null;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

}
