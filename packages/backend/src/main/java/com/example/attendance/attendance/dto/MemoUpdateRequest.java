package com.example.attendance.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemoUpdateRequest(
    @Size(max = 200, message = "メモは200文字以内で入力してください") String memo,
    @NotNull Long version
) {}
