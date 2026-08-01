package com.ktb.community.domain.draft.controller;

import com.ktb.community.domain.draft.dto.DraftPublishRequestDto;
import com.ktb.community.domain.draft.dto.DraftPublishResponseDto;
import com.ktb.community.domain.draft.dto.DraftAutosaveResponseDto;
import com.ktb.community.domain.draft.dto.DraftRequestDto;
import com.ktb.community.domain.draft.dto.DraftResponseDto;
import com.ktb.community.domain.draft.service.DraftCreateResult;
import com.ktb.community.domain.draft.service.DraftService;
import com.ktb.community.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/posts/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<DraftResponseDto>>
    getActiveDraft(@AuthenticationPrincipal UserDetails userDetails) {
        Optional<DraftResponseDto> response =
                draftService.getActiveDraft(userDetails.getUsername());

        if (response.isEmpty()) {
            return ResponseEntity
                    .noContent()
                    .build();
        }

        return ResponseEntity.ok(
                new ApiResponse<>(
                        "get_active_draft_success",
                        response.get()
                )
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DraftResponseDto>>
    createDraft(
            @AuthenticationPrincipal
            UserDetails userDetails,

            @Valid
            @RequestBody
            DraftRequestDto request
    ) {
        DraftCreateResult result = draftService.createDraft(userDetails.getUsername(), request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        String message = result.created() ? "create_draft_success" : "get_existing_draft_success";

        return ResponseEntity
                .status(status)
                .body(
                        new ApiResponse<>(
                                message,
                                result.draft()
                        )
                );
    }

    @DeleteMapping("/{draftId}")
    public ResponseEntity<ApiResponse<Boolean>>
    deleteDraft(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long draftId) {
        draftService.deleteDraft(userDetails.getUsername(), draftId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        "delete_draft_success",
                        true
                )
        );
    }

    @PutMapping("/{draftId}/autosave")
    public ResponseEntity<ApiResponse<DraftAutosaveResponseDto>>
    autosaveDraft(
            @AuthenticationPrincipal
            UserDetails userDetails,

            @PathVariable
            Long draftId,

            @Valid
            @RequestBody
            DraftRequestDto request
    ) {
        DraftAutosaveResponseDto response =
                draftService.autosaveDraft(
                        userDetails.getUsername(),
                        draftId,
                        request
                );

        return ResponseEntity.ok(
                new ApiResponse<>("autosave_draft_success", response)
        );
    }

    @PutMapping("/{draftId}")
    public ResponseEntity<ApiResponse<DraftResponseDto>>
    saveDraft(
            @AuthenticationPrincipal
            UserDetails userDetails,

            @PathVariable
            Long draftId,

            @Valid
            @RequestBody
            DraftRequestDto request
    ) {
        DraftResponseDto response =
                draftService.saveDraft(
                        userDetails.getUsername(),
                        draftId,
                        request
                );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        "save_draft_success",
                        response
                )
        );
    }

    @PostMapping("/{draftId}/publish")
    public ResponseEntity<ApiResponse<DraftPublishResponseDto>>
    publishDraft(
            @AuthenticationPrincipal
            UserDetails userDetails,

            @PathVariable
            Long draftId,

            @Valid
            @RequestBody
            DraftPublishRequestDto request
    ) {
        DraftPublishResponseDto response =
                draftService.publishDraft(
                        userDetails.getUsername(),
                        draftId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        new ApiResponse<>("publish_draft_success", response)
                );
    }
}
