package com.example.attendance.attendance.repository;

import com.example.attendance.attendance.entity.MemoEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemoEditHistoryRepository extends JpaRepository<MemoEditHistory, UUID> {

    @Query("SELECT h FROM MemoEditHistory h JOIN FETCH h.editor WHERE h.attendanceRecord.id = :recordId ORDER BY h.editedAt DESC")
    List<MemoEditHistory> findByAttendanceRecordIdOrderByEditedAtDesc(@Param("recordId") UUID recordId);
}
