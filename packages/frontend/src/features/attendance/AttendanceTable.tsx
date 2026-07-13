"use client";

import { type Column, DataTable } from "@/components/DataTable";
import { Badge } from "@/components/ui/badge";
import type { DailyAttendanceResponse } from "./attendance-api";
import { formatDate, formatMinutes, formatTime } from "./format";

function firstClockIn(day: DailyAttendanceResponse): string {
  const record = day.records[0];
  return record ? formatTime(record.clockIn) : "--:--";
}

function lastClockOut(day: DailyAttendanceResponse): string {
  const last = day.records[day.records.length - 1];
  return last?.clockOut ? formatTime(last.clockOut) : "--:--";
}

function hasCorrected(day: DailyAttendanceResponse): boolean {
  return day.records.some((r) => r.corrected);
}

const columns: Column<DailyAttendanceResponse>[] = [
  {
    key: "date",
    header: "日付",
    render: (day) => formatDate(day.date),
  },
  {
    key: "clockIn",
    header: "出勤",
    render: (day) => firstClockIn(day),
  },
  {
    key: "clockOut",
    header: "退勤",
    render: (day) => lastClockOut(day),
  },
  {
    key: "workMinutes",
    header: "勤務時間",
    render: (day) => (day.workMinutes > 0 ? formatMinutes(day.workMinutes) : "-"),
  },
  {
    key: "breakMinutes",
    header: "休憩",
    render: (day) => (day.breakMinutes > 0 ? formatMinutes(day.breakMinutes) : "-"),
  },
  {
    key: "overtimeMinutes",
    header: "残業",
    render: (day) => (day.overtimeMinutes > 0 ? formatMinutes(day.overtimeMinutes) : "-"),
  },
  {
    key: "memo",
    header: "メモ",
    render: (day) => {
      const record = day.records[0];
      if (!record?.memo) return null;
      return (
        <span className="text-sm truncate max-w-[150px] inline-block" title={record.memo}>
          {record.memo}
        </span>
      );
    },
  },
  {
    key: "corrected",
    header: "",
    render: (day) => (hasCorrected(day) ? <Badge variant="outline">修正</Badge> : null),
  },
];

interface AttendanceTableProps {
  days: DailyAttendanceResponse[];
}

export function AttendanceTable({ days }: AttendanceTableProps) {
  return (
    <DataTable<DailyAttendanceResponse & Record<string, unknown>>
      columns={columns as Column<DailyAttendanceResponse & Record<string, unknown>>[]}
      data={days as (DailyAttendanceResponse & Record<string, unknown>)[]}
      rowKey={(item) => item.date}
      emptyMessage="勤怠データがありません"
    />
  );
}
