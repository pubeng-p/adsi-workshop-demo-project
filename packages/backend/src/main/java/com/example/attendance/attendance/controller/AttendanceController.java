package com.example.attendance.attendance.controller;

import com.example.attendance.attendance.dto.AttendanceHistoryResponse;
import com.example.attendance.attendance.dto.AttendanceRecordResponse;
import com.example.attendance.attendance.dto.ClockMemoRequest;
import com.example.attendance.attendance.dto.MemoEditHistoryResponse;
import com.example.attendance.attendance.dto.MemoUpdateRequest;
import com.example.attendance.attendance.dto.TeamMemberSummaryResponse;
import com.example.attendance.attendance.dto.TodayStatusResponse;
import com.example.attendance.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/clock-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceRecordResponse clockIn(
            @RequestParam UUID employeeId,
            @Valid @RequestBody(required = false) ClockMemoRequest request) {
        String memo = request != null ? request.memo() : null;
        return attendanceService.clockIn(employeeId, memo);
    }

    @PostMapping("/clock-out")
    public AttendanceRecordResponse clockOut(
            @RequestParam UUID employeeId,
            @Valid @RequestBody(required = false) ClockMemoRequest request) {
        String memo = request != null ? request.memo() : null;
        return attendanceService.clockOut(employeeId, memo);
    }

    @GetMapping("/today")
    public TodayStatusResponse getTodayStatus(@RequestParam UUID employeeId) {
        return attendanceService.getTodayStatus(employeeId);
    }

    @GetMapping("/history")
    public AttendanceHistoryResponse getHistory(
            @RequestParam UUID employeeId,
            @RequestParam String month) {
        return attendanceService.getHistory(employeeId, month);
    }

    @GetMapping("/team")
    public List<TeamMemberSummaryResponse> getTeamAttendance(
            @RequestParam UUID managerId,
            @RequestParam String month) {
        return attendanceService.getTeamAttendance(managerId, month);
    }

    @GetMapping("/all")
    public List<TeamMemberSummaryResponse> getAllAttendance(
            @RequestParam String month,
            @RequestParam(required = false) UUID departmentId) {
        return attendanceService.getAllAttendance(month, departmentId);
    }

    @PutMapping("/{id}/memo")
    public AttendanceRecordResponse updateMemo(
            @PathVariable UUID id,
            @RequestParam UUID editorId,
            @Valid @RequestBody MemoUpdateRequest request) {
        return attendanceService.updateMemo(id, editorId, request.memo(), request.version());
    }

    @GetMapping("/{id}/memo/history")
    public List<MemoEditHistoryResponse> getMemoEditHistory(@PathVariable UUID id) {
        return attendanceService.getMemoEditHistory(id);
    }
}
