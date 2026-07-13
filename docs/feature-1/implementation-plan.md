# 打刻メモ機能 — 実装計画

## 背景・目的

現在の出勤打刻はワンクリックで記録されるが、「直行」「在宅勤務」「客先訪問」等の補足情報を残す手段がない。出退勤共通のメモ欄を追加し、履歴画面で確認・編集できるようにする。編集履歴テーブルで「誰が・いつ・何を変更したか」を監査可能にする。

## 確定要件

- メモはレコードに1つ（出退勤共通）
- 入力任意、最大200文字
- 入力UIはボタンの上にテキストエリア（打刻画面・ダッシュボード両方）
- 履歴画面に「メモ」列追加
- いつでも編集可能（履歴画面 + TodayRecords 両方）
- 編集履歴テーブル（誰が・いつ・旧値→新値）

---

## 1. DB マイグレーション

### V6__add_memo_to_attendance_records.sql

```sql
ALTER TABLE attendance_records ADD COLUMN memo VARCHAR(200);
```

### V7__create_memo_edit_history.sql

```sql
CREATE TABLE memo_edit_history (
    id UUID PRIMARY KEY,
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id),
    editor_id UUID NOT NULL REFERENCES employees(id),
    old_memo VARCHAR(200),
    new_memo VARCHAR(200),
    edited_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_memo_edit_history_record ON memo_edit_history(attendance_record_id);
CREATE INDEX idx_memo_edit_history_editor ON memo_edit_history(editor_id);
```

---

## 2. Backend 変更

### 2.1 Entity

**変更: `AttendanceRecord.java`** — `memo` フィールド追加:

```java
@Column(length = 200)
private String memo;
```

**新規: `MemoEditHistory.java`** — 既存 Entity パターンに準拠:

```java
@Entity
@Table(name = "memo_edit_history")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoEditHistory {

    @Id
    private UUID id;    // Service 層で UuidCreator.getTimeOrderedEpoch()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id", nullable = false)
    private Employee editor;

    @Column(length = 200)
    private String oldMemo;

    @Column(length = 200)
    private String newMemo;

    @Column(nullable = false)
    private Instant editedAt;
}
```

注: append-only テーブルなので `@Version` / `@CreatedDate` / `@LastModifiedDate` は不要。

### 2.2 Repository

**新規: `MemoEditHistoryRepository.java`**

```java
public interface MemoEditHistoryRepository extends JpaRepository<MemoEditHistory, UUID> {
    List<MemoEditHistory> findByAttendanceRecordIdOrderByEditedAtDesc(UUID attendanceRecordId);
}
```

### 2.3 DTO

既存パターンに合わせ、すべて `record` + Bean Validation 直付け:

**変更: `AttendanceRecordResponse.java`** — `memo` 追加:

```java
public record AttendanceRecordResponse(
    UUID id,
    LocalDate workDate,
    Instant clockIn,
    Instant clockOut,
    boolean corrected,
    String memo
) {
    public static AttendanceRecordResponse from(AttendanceRecord record) {
        return new AttendanceRecordResponse(
            record.getId(),
            record.getWorkDate(),
            record.getClockIn(),
            record.getClockOut(),
            record.isCorrected(),
            record.getMemo()
        );
    }
}
```

**新規: `ClockInRequest.java`**

```java
public record ClockInRequest(
    @Size(max = 200, message = "メモは200文字以内で入力してください") String memo
) {}
```

**新規: `ClockOutRequest.java`** — 同構造（退勤時にもメモ設定/上書き可能）

**新規: `MemoUpdateRequest.java`**

```java
public record MemoUpdateRequest(
    @Size(max = 200, message = "メモは200文字以内で入力してください") String memo,
    @NotNull Long version
) {}
```

**新規: `MemoEditHistoryResponse.java`**

```java
public record MemoEditHistoryResponse(
    UUID id,
    UUID editorId,
    String editorName,
    String oldMemo,
    String newMemo,
    Instant editedAt
) {
    public static MemoEditHistoryResponse from(MemoEditHistory history) {
        return new MemoEditHistoryResponse(
            history.getId(),
            history.getEditor().getId(),
            history.getEditor().getName(),
            history.getOldMemo(),
            history.getNewMemo(),
            history.getEditedAt()
        );
    }
}
```

### 2.4 Service

**変更: `AttendanceService.java`（interface）**

```java
AttendanceRecordResponse clockIn(UUID employeeId, String memo);
AttendanceRecordResponse clockOut(UUID employeeId, String memo);
AttendanceRecordResponse updateMemo(UUID recordId, UUID editorId, String memo, Long version);
List<MemoEditHistoryResponse> getMemoEditHistory(UUID recordId);
```

**変更: `AttendanceServiceImpl.java`**

- `clockIn`: builder に `.memo(memo)` 追加
- `clockOut`: memo が非 null なら `record.setMemo(memo)` で上書き
- `updateMemo`:
  - `attendanceRepository.findById(recordId).orElseThrow(() -> new EntityNotFoundException(...))`
  - 旧値保存 → `record.setMemo(memo)` → save（`@Version` で楽観ロック）
  - `MemoEditHistory` を `UuidCreator.getTimeOrderedEpoch()` + `Instant.now(clock)` で作成 → save
  - `AttendanceRecordResponse.from(saved)` を返す
- `getMemoEditHistory`: リポジトリから取得 → `stream().map(MemoEditHistoryResponse::from).toList()`

エラーハンドリング:
- レコード未存在: `EntityNotFoundException` → GlobalExceptionHandler が 404 を返す
- version 不一致: JPA `@Version` により `OptimisticLockException` → GlobalExceptionHandler が 409 を返す

### 2.5 Controller

**変更: `AttendanceController.java`**

```java
@PostMapping("/clock-in")
@ResponseStatus(HttpStatus.CREATED)
public AttendanceRecordResponse clockIn(
        @RequestParam UUID employeeId,
        @Valid @RequestBody(required = false) ClockInRequest request) {
    String memo = request != null ? request.memo() : null;
    return attendanceService.clockIn(employeeId, memo);
}

@PostMapping("/clock-out")
public AttendanceRecordResponse clockOut(
        @RequestParam UUID employeeId,
        @Valid @RequestBody(required = false) ClockOutRequest request) {
    String memo = request != null ? request.memo() : null;
    return attendanceService.clockOut(employeeId, memo);
}

@PutMapping("/{id}/memo")
public AttendanceRecordResponse updateMemo(
        @PathVariable UUID id,
        @RequestParam UUID editorId,
        @Valid @RequestBody MemoUpdateRequest request) {
    return attendanceService.updateMemo(id, editorId, request.memo(), request.version());
}

@GetMapping("/{id}/memo/history")
public List<MemoEditHistoryResponse> getMemoEditHistory(@PathVariable UUID id) {
    return attendanceService.getMemoEditHistory(id);
}
```

### 後方互換性

`@RequestBody(required = false)` により、ボディなしの既存リクエスト（フロントエンド未更新時）も動作する。

---

## 3. Frontend 変更

### 3.1 型・API (`attendance-api.ts`)

既存パターン: 型は同ファイルで `interface` 定義、関数は `apiClient` を使い `Promise<T>` を返す。

```typescript
// 既存 interface に追加
export interface AttendanceRecordResponse {
  id: string;
  workDate: string;
  clockIn: string;
  clockOut: string | null;
  corrected: boolean;
  memo: string | null;  // 追加
}

// 新規 interface
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

// 関数変更: memo パラメータ追加
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

// 新規関数
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
  return apiClient.get<MemoEditHistoryResponse[]>(
    `/api/attendance/${recordId}/memo/history`,
  );
}
```

### 3.2 Hooks (`useAttendance.ts`)

既存パターン: `const KEY = [...] as const`、`useAuth()` で user 取得、`useMutation` + invalidate + toast。

```typescript
// 変更: useClockIn
export function useClockIn() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memo?: string) => clockIn(user!.id, memo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TODAY_STATUS_KEY });
      toast.success("出勤を記録しました");
    },
    onError: () => { toast.error("出勤の記録に失敗しました"); },
  });
}

// 変更: useClockOut（同様に memo 引数追加）

// 新規: useUpdateMemo
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
    onError: () => { toast.error("メモの更新に失敗しました"); },
  });
}

// 新規: useMemoEditHistory
const MEMO_HISTORY_KEY = ["attendance", "memo-history"] as const;

export function useMemoEditHistory(recordId: string | null) {
  return useQuery({
    queryKey: [...MEMO_HISTORY_KEY, recordId],
    queryFn: () => fetchMemoEditHistory(recordId!),
    enabled: !!recordId,
  });
}
```

### 3.3 コンポーネント

#### 新規: `MemoInput.tsx`

```typescript
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
```

#### 新規: `MemoEditDialog.tsx`

既存パターンに合わせ `<FormDialog>` ラッパーを使用:

```typescript
"use client";

import { useEffect, useState } from "react";
import { FormDialog } from "@/components/FormDialog";
import { useUpdateMemo } from "./useAttendance";

interface MemoEditDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  recordId: string;
  currentMemo: string | null;
  version: number;
}

export function MemoEditDialog({
  open, onOpenChange, recordId, currentMemo, version,
}: MemoEditDialogProps) {
  const [memo, setMemo] = useState(currentMemo ?? "");
  const mutation = useUpdateMemo();

  useEffect(() => {
    if (open) setMemo(currentMemo ?? "");
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
      <textarea ... />  {/* MemoInput 相当の textarea + カウンター */}
    </FormDialog>
  );
}
```

#### 変更: `ClockButtons.tsx`

ボタングリッドの上に `<MemoInput>` を追加:

```typescript
export function ClockButtons() {
  const [memo, setMemo] = useState("");
  // ... 既存 hooks ...

  return (
    <div className="rounded-lg border p-6 space-y-4">
      <CurrentTime />
      {/* ステータス表示 */}
      <MemoInput value={memo} onChange={setMemo} disabled={isPending} />
      <div className="grid grid-cols-2 gap-4 max-w-md mx-auto">
        <button onClick={() => clockInMutation.mutate(memo || undefined)} ...>出勤</button>
        <button onClick={() => clockOutMutation.mutate(memo || undefined)} ...>退勤</button>
      </div>
    </div>
  );
}
```

成功時に `setMemo("")` でクリア（onSuccess コールバック内）。

#### 変更: `AttendanceTable.tsx`

既存の `columns` 配列に「メモ」列を追加:

```typescript
{
  key: "memo",
  header: "メモ",
  render: (day) => {
    const record = day.records[0];
    if (!record?.memo) return null;
    return (
      <div className="flex items-center gap-1">
        <span className="text-sm truncate max-w-[150px]" title={record.memo}>
          {record.memo}
        </span>
        <button onClick={() => openEditDialog(record)} ...>
          <Pencil className="h-3 w-3" />
        </button>
      </div>
    );
  },
}
```

`MemoEditDialog` を state で制御し、テーブル下に配置。

#### 変更: `TodayRecords.tsx`

各レコードの行にメモを表示 + 編集ボタン:

```typescript
{record.memo && (
  <span className="text-xs text-muted-foreground">{record.memo}</span>
)}
<button onClick={() => openEditDialog(record)}>
  <Pencil className="h-3 w-3" />
</button>
```

---

## 4. 実装順序

1. DB マイグレーション（V6, V7）
2. Entity（`AttendanceRecord` に memo 追加 + `MemoEditHistory` 新規）
3. Repository（`MemoEditHistoryRepository` 新規）
4. DTO（`ClockInRequest`, `ClockOutRequest`, `MemoUpdateRequest`, `MemoEditHistoryResponse` 新規 + `AttendanceRecordResponse` 変更）
5. Service 層（TDD: テスト先行）
6. Controller 層（TDD: テスト先行）
7. Frontend 型 + API クライアント
8. Frontend hooks
9. `MemoInput` コンポーネント
10. `ClockButtons` 修正
11. `MemoEditDialog` コンポーネント
12. `AttendanceTable` 修正
13. `TodayRecords` 修正

---

## 5. テスト方針

### Backend

**Service テスト** (`AttendanceServiceTest` に追加、`@ExtendWith(MockitoExtension.class)` + `@Nested`):

```java
@Nested
@DisplayName("clockIn")
class ClockIn {
    @Test
    @DisplayName("メモ付きで出勤打刻するとメモが保存される")
    void clockIn_withMemo_savesMemo() { ... }

    @Test
    @DisplayName("メモなしで出勤打刻するとメモはnull")
    void clockIn_withoutMemo_savesNull() { ... }
}

@Nested
@DisplayName("updateMemo")
class UpdateMemo {
    @Test
    @DisplayName("正常系: メモ更新と編集履歴が保存される")
    void updateMemo_validRequest_updatesAndCreatesHistory() { ... }

    @Test
    @DisplayName("存在しないレコードIDで404")
    void updateMemo_nonExistentRecord_throwsNotFound() { ... }

    @Test
    @DisplayName("version不一致で楽観ロックエラー")
    void updateMemo_staleVersion_throwsConflict() { ... }
}
```

**Controller テスト** (`@WebMvcTest` + `@MockitoBean`):
- `POST /clock-in` with JSON body containing memo → 201
- `POST /clock-in` with no body → 201（後方互換）
- `POST /clock-in` with 201-char memo → 400
- `PUT /{id}/memo` → 200
- `GET /{id}/memo/history` → 200

**Repository テスト** (`@DataJpaTest` + `@Import(JpaAuditingConfig.class)`):
- `findByAttendanceRecordIdOrderByEditedAtDesc` が降順で返る

### Frontend

**テストパターン**: Vitest + Testing Library。UI プリミティブは `vi.mock`。説明は日本語。

- **`MemoInput.test.tsx`**: テキストエリア表示、入力で onChange 呼び出し、文字数カウント表示
- **`ClockButtons.test.tsx`（既存に追加）**: テキストエリアが存在する、memo が mutate に渡される
- **`MemoEditDialog.test.tsx`**: 開いたとき currentMemo がプリフィルされる、保存で mutation 発火、キャンセルで閉じる

---

## 6. 検証方法

```bash
# Backend ビルド + テスト
cd packages/backend && ./mvnw clean test

# Frontend lint + テスト
cd packages/frontend && npm run lint && npm test

# 統合確認
npm run dev:sagemaker
# ブラウザで:
#   1. 出勤画面でメモ入力 → 出勤ボタン → TodayRecords にメモ表示
#   2. TodayRecords の編集ボタン → ダイアログでメモ編集
#   3. 履歴画面でメモ列に値が表示される
#   4. 履歴画面の編集ボタン → ダイアログで編集 → 反映確認
#   5. メモ空で出勤 → 正常に打刻される（任意確認）
```

---

## 7. 主要ファイルパス

### 変更対象（既存）

| ファイル | 変更内容 |
|---------|---------|
| `packages/backend/.../attendance/entity/AttendanceRecord.java` | `memo` フィールド追加 |
| `packages/backend/.../attendance/dto/AttendanceRecordResponse.java` | `memo` 追加 + `from()` 更新 |
| `packages/backend/.../attendance/service/AttendanceService.java` | シグネチャ変更 + 新メソッド |
| `packages/backend/.../attendance/service/AttendanceServiceImpl.java` | clockIn/clockOut 修正 + updateMemo/getMemoEditHistory 実装 |
| `packages/backend/.../attendance/controller/AttendanceController.java` | RequestBody 追加 + 新エンドポイント |
| `packages/frontend/src/features/attendance/attendance-api.ts` | 型 + 関数追加 |
| `packages/frontend/src/features/attendance/useAttendance.ts` | hooks 修正 + 新規 hooks |
| `packages/frontend/src/features/attendance/ClockButtons.tsx` | MemoInput 追加 + mutate 引数変更 |
| `packages/frontend/src/features/attendance/AttendanceTable.tsx` | メモ列 + 編集ボタン |
| `packages/frontend/src/features/attendance/TodayRecords.tsx` | メモ表示 + 編集ボタン |

### 新規作成

| ファイル | 役割 |
|---------|------|
| `packages/backend/src/main/resources/db/migration/V6__add_memo_to_attendance_records.sql` | memo カラム追加 |
| `packages/backend/src/main/resources/db/migration/V7__create_memo_edit_history.sql` | 編集履歴テーブル |
| `packages/backend/.../attendance/entity/MemoEditHistory.java` | 編集履歴 Entity |
| `packages/backend/.../attendance/repository/MemoEditHistoryRepository.java` | 編集履歴 Repository |
| `packages/backend/.../attendance/dto/ClockInRequest.java` | 出勤リクエスト DTO |
| `packages/backend/.../attendance/dto/ClockOutRequest.java` | 退勤リクエスト DTO |
| `packages/backend/.../attendance/dto/MemoUpdateRequest.java` | メモ更新リクエスト DTO |
| `packages/backend/.../attendance/dto/MemoEditHistoryResponse.java` | 編集履歴レスポンス DTO |
| `packages/frontend/src/features/attendance/MemoInput.tsx` | メモ入力コンポーネント |
| `packages/frontend/src/features/attendance/MemoEditDialog.tsx` | メモ編集ダイアログ |
| `packages/frontend/src/features/attendance/MemoInput.test.tsx` | テスト |
| `packages/frontend/src/features/attendance/MemoEditDialog.test.tsx` | テスト |
