package com.ktb.community.domain.draft.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.draft.dto.DraftRequestDto;
import com.ktb.community.domain.draft.dto.DraftResponseDto;
import com.ktb.community.domain.draft.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @PostMapping
    public ResponseEntity<ApiResponse<DraftResponseDto>> createDraft(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody DraftRequestDto request
    ) {
        DraftResponseDto response = draftService.createDraft(userDetails.getUsername(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("create_draft_success", response));
    }

    @PutMapping("/{draftId}/autosave")
    public ResponseEntity<ApiResponse<DraftResponseDto>> autosaveDraft(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long draftId,
            @RequestBody DraftRequestDto request
    ) {
        DraftResponseDto response = draftService.autosaveDraft(userDetails.getUsername(), draftId, request);

        return ResponseEntity.ok(
                new ApiResponse<>("autosave_draft_success", response)
        );
    }
}
