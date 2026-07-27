package com.ktb.community.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass   // 클래스 자체를 테이블로 만들지 않고, 필드만 자식 엔티티 테이블에 상속
@EntityListeners(AuditingEntityListener.class)  // 이 엔티티의 수정 / 저장 이벤트를 감지해서 Auditing을 적용
public abstract class BaseTimeEntity {

    @CreatedDate    // 처음 저장될 때 자동으로 생성 시각 들어감
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate    // 수정될 때 자동으로 해당 시각으로 바뀜
    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;
}