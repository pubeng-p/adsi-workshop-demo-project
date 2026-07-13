package com.example.attendance.attendance.dto;

import com.example.attendance.attendance.entity.MemoEditHistory;

import java.time.Instant;
import java.util.UUID;

public record MemoEditHistoryResponse(
    UUID id,
    UUID editorId,
    String editorName,
    String oldMemo,
    String newMemo,
    Instant editedAt
) {
    public static MemoEditHistoryResponse from(MemoEditHistory history) {
        return new MemoEditHistoryResponse(
            history.getId(),
            history.getEditor().getId(),
            history.getEditor().getName(),
            history.getOldMemo(),
            history.getNewMemo(),
            history.getEditedAt()
        );
    }
}
