import { apiClient } from "@/lib/api-client";

export interface AttendanceRecordResponse {
  id: string;
  workDate: string;
  clockIn: string;
  clockOut: string | null;
  corrected: boolean;
  memo: string | null;
}

export interface TodayStatusResponse {
  status: "NOT_CLOCKED_IN" | "CLOCKED_IN" | "CLOCKED_OUT";
  records: AttendanceRecordResponse[];
}

export interface DailyAttendanceResponse {
  date: string;
  records: AttendanceRecordResponse[];
  totalWorkMinutes: number;
  breakMinutes: number;
  workMinutes: number;
  overtimeMinutes: number;
}

export interface MonthlySummaryResponse {
  workDays: number;
  totalWorkMinutes: number;
  totalOvertimeMinutes: number;
  absentDays: number;
}

export interface AttendanceHistoryResponse {
  month: string;
  days: DailyAttendanceResponse[];
  summary: MonthlySummaryResponse;
}

export interface TeamMemberSummaryResponse {
  employeeId: string;
  employeeName: string;
  workDays: number;
  totalWorkMinutes: number;
  totalOvertimeMinutes: number;
  absentDays: number;
}

export interface MemoUpdateRequest {
  memo: string | null;
  version: number;
}

export interface MemoEditHistoryResponse {
  id: string;
  editorId: string;
  editorName: string;
  oldMemo: string | null;
  newMemo: string | null;
  editedAt: string;
}

export function clockIn(employeeId: string, memo?: string): Promise<AttendanceRecordResponse> {
  return apiClient.post<AttendanceRecordResponse>(
    `/api/attendance/clock-in?employeeId=${employeeId}`,
    memo != null ? { memo } : undefined,
  );
}

export function clockOut(employeeId: string, memo?: string): Promise<AttendanceRecordResponse> {
  return apiClient.post<AttendanceRecordResponse>(
    `/api/attendance/clock-out?employeeId=${employeeId}`,
    memo != null ? { memo } : undefined,
  );
}

export function updateMemo(
  recordId: string,
  editorId: string,
  request: MemoUpdateRequest,
): Promise<AttendanceRecordResponse> {
  return apiClient.put<AttendanceRecordResponse>(
    `/api/attendance/${recordId}/memo?editorId=${editorId}`,
    request,
  );
}

export function fetchMemoEditHistory(recordId: string): Promise<MemoEditHistoryResponse[]> {
  return apiClient.get<MemoEditHistoryResponse[]>(`/api/attendance/${recordId}/memo/history`);
}

export function fetchTodayStatus(employeeId: string): Promise<TodayStatusResponse> {
  return apiClient.get<TodayStatusResponse>(`/api/attendance/today?employeeId=${employeeId}`);
}

export function fetchHistory(
  employeeId: string,
  month: string,
): Promise<AttendanceHistoryResponse> {
  return apiClient.get<AttendanceHistoryResponse>(
    `/api/attendance/history?employeeId=${employeeId}&month=${month}`,
  );
}

export function fetchTeamAttendance(
  managerId: string,
  month: string,
): Promise<TeamMemberSummaryResponse[]> {
  return apiClient.get<TeamMemberSummaryResponse[]>(
    `/api/attendance/team?managerId=${managerId}&month=${month}`,
  );
}

export function fetchAllAttendance(
  month: string,
  departmentId?: string,
): Promise<TeamMemberSummaryResponse[]> {
  const params = new URLSearchParams({ month });
  if (departmentId) {
    params.set("departmentId", departmentId);
  }
  return apiClient.get<TeamMemberSummaryResponse[]>(`/api/attendance/all?${params.toString()}`);
}
