package com.ktb.community.domain.draft.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class DraftRequestDto {

    private String title;
    private String postBody;
    private String postImage;
}