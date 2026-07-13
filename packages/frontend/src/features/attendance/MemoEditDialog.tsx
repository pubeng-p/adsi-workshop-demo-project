"use client";

import { useEffect, useState } from "react";
import { FormDialog } from "@/components/FormDialog";
import { MemoInput } from "./MemoInput";
import { useUpdateMemo } from "./useAttendance";

interface MemoEditDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  recordId: string;
  currentMemo: string | null;
  version: number;
}

export function MemoEditDialog({
  open,
  onOpenChange,
  recordId,
  currentMemo,
  version,
}: MemoEditDialogProps) {
  const [memo, setMemo] = useState(currentMemo ?? "");
  const mutation = useUpdateMemo();

  useEffect(() => {
    if (open) {
      setMemo(currentMemo ?? "");
    }
  }, [open, currentMemo]);

  const handleSubmit = () => {
    mutation.mutate(
      { recordId, memo: memo || null, version },
      { onSuccess: () => onOpenChange(false) },
    );
  };

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title="メモ編集"
      onSubmit={handleSubmit}
      submitLabel="保存"
      isSubmitting={mutation.isPending}
    >
      <MemoInput value={memo} onChange={setMemo} />
    </FormDialog>
  );
}
