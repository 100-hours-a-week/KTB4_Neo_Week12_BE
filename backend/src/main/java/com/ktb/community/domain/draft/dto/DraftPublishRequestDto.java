package com.ktb.community.domain.draft.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DraftPublishRequestDto {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String postBody;

    @Size(max = 500)
    private String postImage;

    @NotNull
    @Min(1)
    private Long contentVersion;
}
