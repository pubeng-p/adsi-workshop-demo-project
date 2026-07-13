package com.example.attendance.attendance.service;

import com.example.attendance.attendance.domain.AttendanceStatus;
import com.example.attendance.attendance.domain.WorkDuration;
import com.example.attendance.attendance.dto.AttendanceHistoryResponse;
import com.example.attendance.attendance.dto.AttendanceRecordResponse;
import com.example.attendance.attendance.dto.DailyAttendanceResponse;
import com.example.attendance.attendance.dto.MonthlySummaryResponse;
import com.example.attendance.attendance.dto.TeamMemberSummaryResponse;
import com.example.attendance.attendance.dto.TodayStatusResponse;
import com.example.attendance.attendance.entity.AttendanceRecord;
import com.example.attendance.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.repository.EmployeeRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public AttendanceServiceImpl(
            AttendanceRecordRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AttendanceRecordResponse clockIn(UUID employeeId) {
        var employee = findEmployeeOrThrow(employeeId);
        var today = LocalDate.now(clock);

        var existing = attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, today);
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already clocked in today");
        }

        var now = Instant.now(clock);
        var record = AttendanceRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .employee(employee)
                .workDate(today)
                .clockIn(now)
                .corrected(false)
                .build();

        var saved = attendanceRepository.save(record);
        log.info("Clock-in recorded for employee={} at={}", employeeId, now);
        return AttendanceRecordResponse.from(saved);
    }

    @Override
    @Transactional
    public AttendanceRecordResponse clockOut(UUID employeeId) {
        var today = LocalDate.now(clock);
        var record = attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employeeId, today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active clock-in found"));

        record.setClockOut(Instant.now(clock));
        var saved = attendanceRepository.save(record);
        log.info("Clock-out recorded for employee={} at={}", employeeId, saved.getClockOut());
        return AttendanceRecordResponse.from(saved);
    }

    @Override
    public TodayStatusResponse getTodayStatus(UUID employeeId) {
        var today = LocalDate.now(clock);
        var records = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, today);

        AttendanceStatus status;
        if (records.isEmpty()) {
            status = AttendanceStatus.NOT_CLOCKED_IN;
        } else if (records.stream().anyMatch(r -> r.getClockOut() == null)) {
            status = AttendanceStatus.CLOCKED_IN;
        } else {
            status = AttendanceStatus.CLOCKED_OUT;
        }

        var responses = records.stream()
                .map(AttendanceRecordResponse::from)
                .toList();
        return new TodayStatusResponse(status, List.copyOf(responses));
    }

    @Override
    public AttendanceHistoryResponse getHistory(UUID employeeId, String month) {
        var yearMonth = YearMonth.parse(month);
        var start = yearMonth.atDay(1);
        var end = yearMonth.atEndOfMonth();

        var records = attendanceRepository.findByEmployeeIdAndWorkDateBetween(employeeId, start, end);
        var grouped = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));

        var days = buildDailyResponses(grouped, start, end);
        var summary = buildMonthlySummary(grouped, yearMonth);
        return new AttendanceHistoryResponse(month, List.copyOf(days), summary);
    }

    @Override
    public List<TeamMemberSummaryResponse> getTeamAttendance(UUID managerId, String month) {
        var manager = findEmployeeOrThrow(managerId);
        var departmentId = manager.getDepartment().getId();
        var members = employeeRepository.findByDepartmentId(departmentId);
        return buildTeamSummaries(members, month);
    }

    @Override
    public List<TeamMemberSummaryResponse> getAllAttendance(String month, UUID departmentId) {
        List<Employee> employees;
        if (departmentId != null) {
            employees = employeeRepository.findByDepartmentId(departmentId);
        } else {
            employees = employeeRepository.findAll();
        }
        return buildTeamSummaries(employees, month);
    }

    private List<TeamMemberSummaryResponse> buildTeamSummaries(List<Employee> employees, String month) {
        var yearMonth = YearMonth.parse(month);
        var start = yearMonth.atDay(1);
        var end = yearMonth.atEndOfMonth();

        var employeeIds = employees.stream().map(Employee::getId).toList();
        var allRecords = attendanceRepository.findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end);
        var byEmployee = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        return employees.stream()
                .map(emp -> {
                    var empRecords = byEmployee.getOrDefault(emp.getId(), List.of());
                    var grouped = empRecords.stream()
                            .collect(Collectors.groupingBy(AttendanceRecord::getWorkDate));
                    var summary = buildMonthlySummary(grouped, yearMonth);
                    return new TeamMemberSummaryResponse(
                            emp.getId(),
                            emp.getName(),
                            summary.workDays(),
                            summary.totalWorkMinutes(),
                            summary.totalOvertimeMinutes(),
                            summary.absentDays()
                    );
                })
                .toList();
    }

    private List<DailyAttendanceResponse> buildDailyResponses(
            Map<LocalDate, List<AttendanceRecord>> grouped,
            LocalDate start,
            LocalDate end) {
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    var date = entry.getKey();
                    var dayRecords = entry.getValue();
                    var duration = WorkDuration.calculate(dayRecords);
                    var responses = dayRecords.stream()
                            .map(AttendanceRecordResponse::from)
                            .toList();
                    return new DailyAttendanceResponse(
                            date,
                            List.copyOf(responses),
                            duration.totalMinutes(),
                            duration.breakMinutes(),
                            duration.workMinutes(),
                            duration.overtimeMinutes()
                    );
                })
                .toList();
    }

    private MonthlySummaryResponse buildMonthlySummary(
            Map<LocalDate, List<AttendanceRecord>> grouped,
            YearMonth yearMonth) {
        int workDays = grouped.size();

        int totalWorkMinutes = 0;
        int totalOvertimeMinutes = 0;
        for (var dayRecords : grouped.values()) {
            var duration = WorkDuration.calculate(dayRecords);
            totalWorkMinutes += duration.workMinutes();
            totalOvertimeMinutes += duration.overtimeMinutes();
        }

        int weekdaysInMonth = countWeekdays(yearMonth);
        int absentDays = Math.max(0, weekdaysInMonth - workDays);

        return new MonthlySummaryResponse(workDays, totalWorkMinutes, totalOvertimeMinutes, absentDays);
    }

    private int countWeekdays(YearMonth yearMonth) {
        int count = 0;
        var date = yearMonth.atDay(1);
        var end = yearMonth.atEndOfMonth();
        while (!date.isAfter(end)) {
            var dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }

    private Employee findEmployeeOrThrow(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee with id '%s' was not found".formatted(employeeId)));
    }
}
