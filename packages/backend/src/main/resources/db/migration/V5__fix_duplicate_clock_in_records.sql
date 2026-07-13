-- Issue #1: 重複出勤レコードの修復
-- 同一社員・同日に clock_out=NULL のレコードが複数ある場合、最古の1件を残して他を削除する

DELETE FROM attendance_records
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY employee_id, work_date ORDER BY clock_in ASC) AS rn
        FROM attendance_records
        WHERE clock_out IS NULL
    ) ranked
    WHERE rn > 1
);

-- 今後の重複を DB レベルで防止する部分インデックス
CREATE UNIQUE INDEX idx_attendance_one_open_per_day
    ON attendance_records(employee_id, work_date)
    WHERE clock_out IS NULL;
