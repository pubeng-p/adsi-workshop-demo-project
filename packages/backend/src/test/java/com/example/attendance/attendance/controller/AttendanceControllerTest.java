package com.example.attendance.attendance.controller;

import com.example.attendance.attendance.domain.AttendanceStatus;
import com.example.attendance.attendance.dto.AttendanceHistoryResponse;
import com.example.attendance.attendance.dto.AttendanceRecordResponse;
import com.example.attendance.attendance.dto.DailyAttendanceResponse;
import com.example.attendance.attendance.dto.MemoEditHistoryResponse;
import com.example.attendance.attendance.dto.MonthlySummaryResponse;
import com.example.attendance.attendance.dto.TodayStatusResponse;
import com.example.attendance.attendance.service.AttendanceService;
import com.example.attendance.common.config.CorsConfig;
import com.example.attendance.common.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = AttendanceController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, CorsConfig.class}
    )
)
@Import(AttendanceControllerTest.TestSecurityConfig.class)
@ActiveProfiles("test")
class AttendanceControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("POST /api/attendance/clock-in は201を返す")
    void clockIn_validRequest_returns201() throws Exception {
        // Arrange
        var response = new AttendanceRecordResponse(
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 15),
                Instant.parse("2025-01-15T00:00:00Z"),
                null,
                false,
                null,
                0L
        );
        when(attendanceService.clockIn(EMPLOYEE_ID, null)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/attendance/clock-in")
                        .param("employeeId", EMPLOYEE_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workDate").value("2025-01-15"))
                .andExpect(jsonPath("$.clockOut").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/attendance/clock-out は200を返す")
    void clockOut_validRequest_returns200() throws Exception {
        // Arrange
        var response = new AttendanceRecordResponse(
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 15),
                Instant.parse("2025-01-14T23:00:00Z"),
                Instant.parse("2025-01-15T08:00:00Z"),
                false,
                null,
                0L
        );
        when(attendanceService.clockOut(EMPLOYEE_ID, null)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/attendance/clock-out")
                        .param("employeeId", EMPLOYEE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clockOut").exists());
    }

    @Test
    @DisplayName("GET /api/attendance/today は200を返す")
    void getTodayStatus_validRequest_returns200() throws Exception {
        // Arrange
        var response = new TodayStatusResponse(AttendanceStatus.NOT_CLOCKED_IN, List.of());
        when(attendanceService.getTodayStatus(EMPLOYEE_ID)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/attendance/today")
                        .param("employeeId", EMPLOYEE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_CLOCKED_IN"))
                .andExpect(jsonPath("$.records").isArray());
    }

    @Test
    @DisplayName("GET /api/attendance/history は200を返す")
    void getHistory_validRequest_returns200() throws Exception {
        // Arrange
        var dailyResponse = new DailyAttendanceResponse(
                LocalDate.of(2025, 1, 15),
                List.of(),
                540,
                60,
                480,
                0
        );
        var summary = new MonthlySummaryResponse(1, 480, 0, 22);
        var response = new AttendanceHistoryResponse("2025-01", List.of(dailyResponse), summary);
        when(attendanceService.getHistory(EMPLOYEE_ID, "2025-01")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/attendance/history")
                        .param("employeeId", EMPLOYEE_ID.toString())
                        .param("month", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2025-01"))
                .andExpect(jsonPath("$.days").isArray())
                .andExpect(jsonPath("$.summary.workDays").value(1));
    }

    @Test
    @DisplayName("POST /api/attendance/clock-in — メモ付きボディで201を返す")
    void clockIn_withMemoBody_returns201WithMemo() throws Exception {
        // Arrange
        var response = new AttendanceRecordResponse(
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 15),
                Instant.parse("2025-01-15T00:00:00Z"),
                null,
                false,
                "直行",
                0L
        );
        when(attendanceService.clockIn(EMPLOYEE_ID, "直行")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/attendance/clock-in")
                        .param("employeeId", EMPLOYEE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"直行\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memo").value("直行"));
    }

    @Test
    @DisplayName("PUT /api/attendance/{id}/memo — 正常に200を返す")
    void updateMemo_validRequest_returns200() throws Exception {
        // Arrange
        var recordId = UUID.randomUUID();
        var response = new AttendanceRecordResponse(
                recordId,
                LocalDate.of(2025, 1, 15),
                Instant.parse("2025-01-15T00:00:00Z"),
                null,
                false,
                "更新メモ",
                1L
        );
        when(attendanceService.updateMemo(recordId, EMPLOYEE_ID, "更新メモ", 1L)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/attendance/" + recordId + "/memo")
                        .param("editorId", EMPLOYEE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"更新メモ\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").value("更新メモ"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("GET /api/attendance/{id}/memo/history — 200で履歴リストを返す")
    void getMemoEditHistory_validRequest_returns200() throws Exception {
        // Arrange
        var recordId = UUID.randomUUID();
        var historyResponse = new MemoEditHistoryResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                "田中太郎",
                "旧メモ",
                "新メモ",
                Instant.parse("2025-01-15T01:00:00Z")
        );
        when(attendanceService.getMemoEditHistory(recordId)).thenReturn(List.of(historyResponse));

        // Act & Assert
        mockMvc.perform(get("/api/attendance/" + recordId + "/memo/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].editorName").value("田中太郎"))
                .andExpect(jsonPath("$[0].oldMemo").value("旧メモ"))
                .andExpect(jsonPath("$[0].newMemo").value("新メモ"));
    }
}
