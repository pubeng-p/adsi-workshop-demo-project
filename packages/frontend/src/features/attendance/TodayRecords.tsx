"use client";

import { Pencil } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { AttendanceRecordResponse } from "./attendance-api";
import { formatTime } from "./format";
import { MemoEditDialog } from "./MemoEditDialog";
import { useTodayStatus } from "./useAttendance";

export function TodayRecords() {
  const { data: todayStatus, isLoading } = useTodayStatus();
  const [editingRecord, setEditingRecord] = useState<AttendanceRecordResponse | null>(null);

  if (isLoading) {
    return (
      <div className="rounded-lg border p-6 space-y-3">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-full" />
      </div>
    );
  }

  const records = todayStatus?.records ?? [];

  if (records.length === 0) {
    return (
      <div className="rounded-lg border p-6">
        <h3 className="text-sm font-medium mb-2">本日の打刻記録</h3>
        <p className="text-sm text-muted-foreground">打刻記録はありません</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border p-6">
      <h3 className="text-sm font-medium mb-3">本日の打刻記録</h3>
      <div className="space-y-2">
        {records.map((record) => (
          <div
            key={record.id}
            className="flex items-center justify-between text-sm py-1.5 border-b last:border-b-0"
          >
            <div className="flex items-center gap-3">
              <span className="font-medium">{formatTime(record.clockIn)}</span>
              <span className="text-muted-foreground">~</span>
              <span className="font-medium">
                {record.clockOut ? formatTime(record.clockOut) : "--:--"}
              </span>
              {record.memo && <span className="text-xs text-muted-foreground">{record.memo}</span>}
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                aria-label="メモ編集"
                onClick={() => setEditingRecord(record)}
                className="text-muted-foreground hover:text-foreground"
              >
                <Pencil className="h-3 w-3" />
              </button>
              {record.corrected && <Badge variant="outline">修正済み</Badge>}
            </div>
          </div>
        ))}
      </div>
      {editingRecord && (
        <MemoEditDialog
          open={!!editingRecord}
          onOpenChange={(open) => {
            if (!open) setEditingRecord(null);
          }}
          recordId={editingRecord.id}
          currentMemo={editingRecord.memo}
          version={0}
        />
      )}
    </div>
  );
}
