package com.example.attendance.attendance.service;

import com.example.attendance.attendance.domain.AttendanceStatus;
import com.example.attendance.attendance.entity.AttendanceRecord;
import com.example.attendance.attendance.entity.MemoEditHistory;
import com.example.attendance.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.attendance.repository.MemoEditHistoryRepository;
import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import com.example.attendance.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T00:00:00Z");
    private static final ZoneId ZONE_TOKYO = ZoneId.of("Asia/Tokyo");
    private static final LocalDate TODAY_TOKYO = LocalDate.of(2025, 1, 15);

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private MemoEditHistoryRepository memoEditHistoryRepository;

    private AttendanceServiceImpl service;

    private Employee employee;
    private Department department;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(FIXED_INSTANT, ZONE_TOKYO);
        service = new AttendanceServiceImpl(attendanceRepository, employeeRepository, memoEditHistoryRepository, clock);

        department = Department.builder()
                .id(UUID.randomUUID())
                .name("Engineering")
                .build();

        employee = Employee.builder()
                .id(UUID.randomUUID())
                .name("田中太郎")
                .email("tanaka@example.com")
                .password("hashed")
                .department(department)
                .role(Role.EMPLOYEE)
                .isManager(false)
                .hireDate(LocalDate.of(2024, 4, 1))
                .build();
    }

    @Nested
    @DisplayName("出勤打刻")
    class ClockIn {

        @Test
        @DisplayName("正常に出勤打刻ができる")
        void clockIn_normal_createsRecord() {
            // Arrange
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employee.getId(), null);

            // Assert
            assertThat(result.workDate()).isEqualTo(TODAY_TOKYO);
            assertThat(result.clockIn()).isEqualTo(FIXED_INSTANT);
            assertThat(result.clockOut()).isNull();

            var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getEmployee().getId()).isEqualTo(employee.getId());
        }

        @Test
        @DisplayName("既に出勤中の場合は409エラー")
        void clockIn_alreadyClockedIn_throwsConflict() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(FIXED_INSTANT)
                    .build();
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));

            // Act & Assert
            assertThatThrownBy(() -> service.clockIn(employee.getId(), null))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Already clocked in today");
        }

    }

    @Nested
    @DisplayName("退勤打刻")
    class ClockOut {

        @Test
        @DisplayName("正常に退勤打刻ができる")
        void clockOut_normal_setsClockOut() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockOut(employee.getId(), null);

            // Assert
            assertThat(result.clockOut()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("出勤中レコードがない場合は409エラー")
        void clockOut_noClockedIn_throwsConflict() {
            // Arrange
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.clockOut(employee.getId(), null))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("No active clock-in found");
        }
    }

    @Nested
    @DisplayName("当日ステータス取得")
    class GetTodayStatus {

        @Test
        @DisplayName("レコードなしの場合はNOT_CLOCKED_IN")
        void getTodayStatus_noRecords_returnsNotClockedIn() {
            // Arrange
            when(attendanceRepository.findByEmployeeIdAndWorkDate(employee.getId(), TODAY_TOKYO))
                    .thenReturn(List.of());

            // Act
            var result = service.getTodayStatus(employee.getId());

            // Assert
            assertThat(result.status()).isEqualTo(AttendanceStatus.NOT_CLOCKED_IN);
            assertThat(result.records()).isEmpty();
        }

        @Test
        @DisplayName("未退勤レコードありの場合はCLOCKED_IN")
        void getTodayStatus_openRecord_returnsClockedIn() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(FIXED_INSTANT)
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDate(employee.getId(), TODAY_TOKYO))
                    .thenReturn(List.of(openRecord));

            // Act
            var result = service.getTodayStatus(employee.getId());

            // Assert
            assertThat(result.status()).isEqualTo(AttendanceStatus.CLOCKED_IN);
            assertThat(result.records()).hasSize(1);
        }

        @Test
        @DisplayName("全レコード退勤済みの場合はCLOCKED_OUT")
        void getTodayStatus_allClosed_returnsClockedOut() {
            // Arrange
            var closedRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDate(employee.getId(), TODAY_TOKYO))
                    .thenReturn(List.of(closedRecord));

            // Act
            var result = service.getTodayStatus(employee.getId());

            // Assert
            assertThat(result.status()).isEqualTo(AttendanceStatus.CLOCKED_OUT);
            assertThat(result.records()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("勤怠履歴取得")
    class GetHistory {

        @Test
        @DisplayName("日別レコードと勤務時間が計算される")
        void getHistory_withRecords_returnsDailyAndSummary() {
            // Arrange
            var jan15 = LocalDate.of(2025, 1, 15);
            var jan16 = LocalDate.of(2025, 1, 16);

            var record1 = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(jan15)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-15T08:00:00Z"))
                    .corrected(false)
                    .build();
            var record2 = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(jan16)
                    .clockIn(Instant.parse("2025-01-15T23:00:00Z"))
                    .clockOut(Instant.parse("2025-01-16T08:00:00Z"))
                    .corrected(false)
                    .build();

            when(attendanceRepository.findByEmployeeIdAndWorkDateBetween(
                    eq(employee.getId()),
                    eq(LocalDate.of(2025, 1, 1)),
                    eq(LocalDate.of(2025, 1, 31))
            )).thenReturn(List.of(record1, record2));

            // Act
            var result = service.getHistory(employee.getId(), "2025-01");

            // Assert
            assertThat(result.month()).isEqualTo("2025-01");
            assertThat(result.days()).hasSize(2);
            assertThat(result.days().get(0).date()).isEqualTo(jan15);
            assertThat(result.days().get(0).totalWorkMinutes()).isEqualTo(540);
            assertThat(result.summary().workDays()).isEqualTo(2);
            assertThat(result.summary().absentDays()).isEqualTo(21);
        }
    }

    @Nested
    @DisplayName("出勤打刻 — メモ")
    class ClockInWithMemo {

        @Test
        @DisplayName("メモ付きで出勤打刻するとメモがレコードに保存される")
        void clockIn_withMemo_savesMemoOnRecord() {
            // Arrange
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employee.getId(), "在宅勤務");

            // Assert
            var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getMemo()).isEqualTo("在宅勤務");
            assertThat(result.memo()).isEqualTo("在宅勤務");
        }

        @Test
        @DisplayName("メモがnullで出勤打刻するとメモはnullで保存される")
        void clockIn_withNullMemo_savesNullMemo() {
            // Arrange
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockIn(employee.getId(), null);

            // Assert
            var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getMemo()).isNull();
            assertThat(result.memo()).isNull();
        }
    }

    @Nested
    @DisplayName("退勤打刻 — メモ")
    class ClockOutWithMemo {

        @Test
        @DisplayName("メモ付きで退勤打刻するとメモが上書きされる")
        void clockOut_withMemo_overwritesMemo() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .memo("出勤時メモ")
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockOut(employee.getId(), "退勤時メモ");

            // Assert
            assertThat(result.memo()).isEqualTo("退勤時メモ");
        }

        @Test
        @DisplayName("メモがnullで退勤打刻すると既存メモは変更されない")
        void clockOut_withNullMemo_preservesExistingMemo() {
            // Arrange
            var openRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                    .memo("元のメモ")
                    .build();
            when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                    .thenReturn(Optional.of(openRecord));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.clockOut(employee.getId(), null);

            // Assert
            assertThat(result.memo()).isEqualTo("元のメモ");
        }
    }

    @Nested
    @DisplayName("メモ更新")
    class UpdateMemo {

        @Test
        @DisplayName("正常系: メモを更新し編集履歴が作成される")
        void updateMemo_validRequest_updatesAndCreatesHistory() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(FIXED_INSTANT)
                    .memo("旧メモ")
                    .version(0L)
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
            when(attendanceRepository.save(any(AttendanceRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
            when(memoEditHistoryRepository.save(any(MemoEditHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            var result = service.updateMemo(recordId, employee.getId(), "新メモ", 0L);

            // Assert
            assertThat(result.memo()).isEqualTo("新メモ");

            var historyCaptor = ArgumentCaptor.forClass(MemoEditHistory.class);
            verify(memoEditHistoryRepository).save(historyCaptor.capture());
            var history = historyCaptor.getValue();
            assertThat(history.getOldMemo()).isEqualTo("旧メモ");
            assertThat(history.getNewMemo()).isEqualTo("新メモ");
            assertThat(history.getEditor().getId()).isEqualTo(employee.getId());
            assertThat(history.getEditedAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("存在しないレコードIDで404エラー")
        void updateMemo_nonExistentRecord_throwsNotFound() {
            // Arrange
            var nonExistentId = UUID.randomUUID();
            when(attendanceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.updateMemo(nonExistentId, employee.getId(), "メモ", 0L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("version不一致で409エラー")
        void updateMemo_staleVersion_throwsConflict() {
            // Arrange
            var recordId = UUID.randomUUID();
            var record = AttendanceRecord.builder()
                    .id(recordId)
                    .employee(employee)
                    .workDate(TODAY_TOKYO)
                    .clockIn(FIXED_INSTANT)
                    .version(2L)
                    .build();
            when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));

            // Act & Assert
            assertThatThrownBy(() -> service.updateMemo(recordId, employee.getId(), "メモ", 0L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("modified by another user");
        }
    }

    @Nested
    @DisplayName("メモ編集履歴取得")
    class GetMemoEditHistory {

        @Test
        @DisplayName("編集履歴が降順で返される")
        void getMemoEditHistory_multipleEdits_returnsDescending() {
            // Arrange
            var recordId = UUID.randomUUID();
            var history1 = MemoEditHistory.builder()
                    .id(UUID.randomUUID())
                    .attendanceRecord(AttendanceRecord.builder().id(recordId).build())
                    .editor(employee)
                    .oldMemo(null)
                    .newMemo("初回メモ")
                    .editedAt(Instant.parse("2025-01-15T01:00:00Z"))
                    .build();
            var history2 = MemoEditHistory.builder()
                    .id(UUID.randomUUID())
                    .attendanceRecord(AttendanceRecord.builder().id(recordId).build())
                    .editor(employee)
                    .oldMemo("初回メモ")
                    .newMemo("更新メモ")
                    .editedAt(Instant.parse("2025-01-15T02:00:00Z"))
                    .build();
            when(memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(recordId))
                    .thenReturn(List.of(history2, history1));

            // Act
            var result = service.getMemoEditHistory(recordId);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).newMemo()).isEqualTo("更新メモ");
            assertThat(result.get(0).editorName()).isEqualTo("田中太郎");
            assertThat(result.get(1).newMemo()).isEqualTo("初回メモ");
        }

        @Test
        @DisplayName("編集履歴がない場合は空リストを返す")
        void getMemoEditHistory_noHistory_returnsEmptyList() {
            // Arrange
            var recordId = UUID.randomUUID();
            when(memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(recordId))
                    .thenReturn(List.of());

            // Act
            var result = service.getMemoEditHistory(recordId);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
