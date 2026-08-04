package com.ktb.community.domain.draft.service;

import com.ktb.community.domain.draft.repository.DraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DraftCleanupService {

    private final DraftRepository draftRepository;

    @Value("${draft.retention}")
    private Duration retention;

    @Value("${draft.cleanup-batch-size}")
    private int cleanupBatchSize;

    @Transactional
    public int cleanupExpiredDrafts() {
        LocalDateTime cutoff = LocalDateTime.now().minus(retention);

        List<Long> draftIds = draftRepository.findExpiredDraftIds(
                cutoff,
                PageRequest.of(0, cleanupBatchSize)
        );

        if (draftIds.isEmpty()) {
            return 0;
        }

        return draftRepository.deleteAllByDraftIds(draftIds);
    }
}
