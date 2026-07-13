"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@/components/Toast";
import { useAuth } from "@/features/auth/useAuth";
import {
  clockIn,
  clockOut,
  fetchHistory,
  fetchMemoEditHistory,
  fetchTeamAttendance,
  fetchTodayStatus,
  updateMemo,
} from "./attendance-api";

const TODAY_STATUS_KEY = ["attendance", "today"] as const;
const HISTORY_KEY = ["attendance", "history"] as const;
const TEAM_KEY = ["attendance", "team"] as const;

export function useTodayStatus() {
  const { user } = useAuth();
  const employeeId = user?.id;

  return useQuery({
    queryKey: [...TODAY_STATUS_KEY, employeeId],
    queryFn: () => fetchTodayStatus(employeeId!),
    enabled: !!employeeId,
    refetchInterval: 60 * 1000,
  });
}

export function useClockIn() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (memo?: string) => clockIn(user!.id, memo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TODAY_STATUS_KEY });
      toast.success("出勤を記録しました");
    },
    onError: () => {
      toast.error("出勤の記録に失敗しました");
    },
  });
}

export function useClockOut() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (memo?: string) => clockOut(user!.id, memo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TODAY_STATUS_KEY });
      toast.success("退勤を記録しました");
    },
    onError: () => {
      toast.error("退勤の記録に失敗しました");
    },
  });
}

export function useUpdateMemo() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: { recordId: string; memo: string | null; version: number }) =>
      updateMemo(params.recordId, user!.id, { memo: params.memo, version: params.version }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TODAY_STATUS_KEY });
      queryClient.invalidateQueries({ queryKey: HISTORY_KEY });
      toast.success("メモを更新しました");
    },
    onError: () => {
      toast.error("メモの更新に失敗しました");
    },
  });
}

export function useMemoEditHistory(recordId: string | null) {
  return useQuery({
    queryKey: ["attendance", "memo-history", recordId],
    queryFn: () => fetchMemoEditHistory(recordId!),
    enabled: !!recordId,
  });
}

export function useAttendanceHistory(month: string) {
  const { user } = useAuth();
  const employeeId = user?.id;

  return useQuery({
    queryKey: [...HISTORY_KEY, employeeId, month],
    queryFn: () => fetchHistory(employeeId!, month),
    enabled: !!employeeId && !!month,
  });
}

export function useTeamAttendance(month: string) {
  const { user } = useAuth();

  return useQuery({
    queryKey: [...TEAM_KEY, user?.id, month],
    queryFn: () => fetchTeamAttendance(user!.id, month),
    enabled: !!user?.isManager && !!month,
  });
}
