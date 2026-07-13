"use client";

import { Pencil } from "lucide-react";
import { useState } from "react";
import { type Column, DataTable } from "@/components/DataTable";
import { Badge } from "@/components/ui/badge";
import type { AttendanceRecordResponse, DailyAttendanceResponse } from "./attendance-api";
import { formatDate, formatMinutes, formatTime } from "./format";
import { MemoEditDialog } from "./MemoEditDialog";

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

interface AttendanceTableProps {
  days: DailyAttendanceResponse[];
}

export function AttendanceTable({ days }: AttendanceTableProps) {
  const [editingRecord, setEditingRecord] = useState<AttendanceRecordResponse | null>(null);

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
        return (
          <div className="flex items-center gap-1">
            {record?.memo && (
              <span className="text-sm truncate max-w-[150px] inline-block" title={record.memo}>
                {record.memo}
              </span>
            )}
            {record && (
              <button
                type="button"
                aria-label="メモ編集"
                onClick={() => setEditingRecord(record)}
                className="text-muted-foreground hover:text-foreground"
              >
                <Pencil className="h-3 w-3" />
              </button>
            )}
          </div>
        );
      },
    },
    {
      key: "corrected",
      header: "",
      render: (day) => (hasCorrected(day) ? <Badge variant="outline">修正</Badge> : null),
    },
  ];

  return (
    <>
      <DataTable<DailyAttendanceResponse & Record<string, unknown>>
        columns={columns as Column<DailyAttendanceResponse & Record<string, unknown>>[]}
        data={days as (DailyAttendanceResponse & Record<string, unknown>)[]}
        rowKey={(item) => item.date}
        emptyMessage="勤怠データがありません"
      />
      {editingRecord && (
        <MemoEditDialog
          open={!!editingRecord}
          onOpenChange={(open) => {
            if (!open) setEditingRecord(null);
          }}
          recordId={editingRecord.id}
          currentMemo={editingRecord.memo}
          version={editingRecord.version}
        />
      )}
    </>
  );
}
