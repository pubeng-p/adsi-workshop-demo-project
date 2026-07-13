"use client";

interface MemoInputProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

export function MemoInput({ value, onChange, disabled }: MemoInputProps) {
  return (
    <div className="space-y-1">
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        placeholder="メモ（任意・200文字以内）"
        maxLength={200}
        rows={2}
        className="w-full rounded-md border px-3 py-2 text-sm resize-none disabled:opacity-50"
      />
      <p className="text-xs text-muted-foreground text-right">{value.length}/200</p>
    </div>
  );
}
