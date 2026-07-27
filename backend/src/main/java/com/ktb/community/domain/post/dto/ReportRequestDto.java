package com.ktb.community.domain.post.dto;

import com.ktb.community.domain.post.entity.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class ReportRequestDto {

    @NotNull(message = "신고 유형을 선택해주세요.")
    private ReportType reportType;

    private String reason;
}
