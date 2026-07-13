package com.example.attendance.attendance.repository;

import com.example.attendance.attendance.entity.MemoEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoEditHistoryRepository extends JpaRepository<MemoEditHistory, UUID> {
    List<MemoEditHistory> findByAttendanceRecordIdOrderByEditedAtDesc(UUID attendanceRecordId);
}
