package com.ktb.community.domain.draft.service;

import com.ktb.community.domain.draft.dto.DraftRequestDto;
import com.ktb.community.domain.draft.dto.DraftResponseDto;
import com.ktb.community.domain.draft.entity.Draft;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.domain.draft.repository.DraftRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DraftService {

    private final DraftRepository draftRepository;
    private final UserRepository userRepository;

    public DraftResponseDto createDraft(String email, DraftRequestDto request) {
        User user = getActiveUser(email);
        Draft draft = new Draft(
                user,
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage()
        );

        Draft savedDraft = draftRepository.save(draft);
        return toResponse(savedDraft);
    }

    public DraftResponseDto autosaveDraft(String email, Long draftId, DraftRequestDto request) {
        User user = getActiveUser(email);
        Draft draft = draftRepository.findByDraftIdAndUser(draftId, user)
                .orElseThrow(() -> new ApiException(ErrorCode.DRAFT_NOT_FOUND));

        if (draft.isPublished()) {
            throw new ApiException(ErrorCode.DRAFT_ALREADY_PUBLISHED);
        }

        draft.autosave(
                request.getTitle(),
                request.getPostBody(),
                request.getPostImage()
        );

        return toResponse(draft);
    }

    private DraftResponseDto toResponse(Draft draft) {
        return new DraftResponseDto(
                draft.getDraftId(),
                draft.getTitle(),
                draft.getPostBody(),
                draft.getPostImage(),
                draft.getUpdatedAt()
        );
    }

    private User getActiveUser(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_USER));

        return user;
    }
}
