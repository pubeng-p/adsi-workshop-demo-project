# Unit 04: 打刻・勤怠履歴

出退勤の打刻と勤怠履歴表示。勤務時間計算（WorkDuration）を含む。

## 依存関係

- 依存先: Unit 00（共通基盤）, Unit 02（社員 — FK 参照）, Unit 03（認証 — ログインユーザー特定）
- 依存元: Unit 05（修正 — AttendanceRecord 参照）, Unit 06（集計 — 勤怠データ参照）

## ユーザーストーリー

- **ATT-01**: 社員として、出勤ボタンを押して出勤打刻したい
- **ATT-02**: 社員として、退勤ボタンを押して退勤打刻したい
- **ATT-03**: 社員として、当日の打刻状態を確認したい
- **ATT-04**: 出勤中（未退勤）の場合、再度出勤打刻はできない
- **ATT-05**: 出勤していない場合、退勤打刻はできない
- **ATT-06**: 退勤後に再出勤できる（同日複数回の出退勤）
- **HIST-01**: 社員として、月別の自分の勤怠履歴を確認したい
- **HIST-02**: 勤務時間が自動計算されて表示される
- **HIST-03**: 修正済みレコードにはマークが表示される
- **HIST-04**: 月間の出勤日数・勤務時間合計・残業時間・欠勤日数のサマリーが表示される
- **HIST-05**: 上長として、自部署メンバーの月別勤怠サマリーを確認したい
- **HIST-06**: 管理者として、全社員の勤怠サマリーを確認したい（部署フィルタ付き）

## テーブル

### attendance_records

```sql
CREATE TABLE attendance_records (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    work_date DATE NOT NULL,
    clock_in TIMESTAMPTZ NOT NULL,
    clock_out TIMESTAMPTZ,
    corrected BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attendance_records_employee_date ON attendance_records(employee_id, work_date);
```

Flyway: `V3__create_attendance_records.sql`

## API

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| POST | `/api/attendance/clock-in` | 出勤打刻 | 全ロール |
| POST | `/api/attendance/clock-out` | 退勤打刻 | 全ロール |
| GET | `/api/attendance/today` | 当日の打刻状態 | 全ロール |
| GET | `/api/attendance/history` | 自分の勤怠履歴（月指定） | 全ロール |
| GET | `/api/attendance/team` | 自部署メンバーの勤怠 | 上長 |
| GET | `/api/attendance/all` | 全社員の勤怠 | 管理者 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/` | ダッシュボード（打刻） | `ClockButtons`, `TodayRecords`, `StatusBadge` |
| `/history` | 勤怠履歴 | `MonthSelector`, `AttendanceTable`, `MonthlySummary` |
| `/team` | 部署メンバー勤怠 | `MonthSelector`, `TeamAttendanceTable` |
| `/admin/attendance` | 全社員勤怠 | `MonthSelector`, `AllAttendanceTable` |

## Backend 実装順序（TDD）

1. Flyway マイグレーション `V3__create_attendance_records.sql`
2. `AttendanceRecord` Entity
3. `WorkDuration` Value Object（勤務時間計算ロジック）
4. `AttendanceRepository` テスト → 実装
5. `AttendanceService` テスト → interface → 実装（打刻 + 履歴 + チーム + 全社）
6. `AttendanceController` テスト → 実装
7. 統合テスト

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/attendance/
│   ├── controller/AttendanceController.java
│   ├── dto/
│   │   ├── AttendanceRecordResponse.java     (record)
│   │   ├── TodayStatusResponse.java          (record)
│   │   ├── AttendanceHistoryResponse.java    (record)
│   │   ├── DailyAttendanceResponse.java      (record)
│   │   ├── MonthlySummaryResponse.java       (record)
│   │   └── TeamMemberSummaryResponse.java    (record)
│   ├── entity/AttendanceRecord.java
│   ├── domain/
│   │   ├── WorkDuration.java                 (Value Object)
│   │   └── AttendanceStatus.java             (enum: NOT_CLOCKED_IN, CLOCKED_IN, CLOCKED_OUT)
│   ├── repository/AttendanceRecordRepository.java
│   └── service/
│       ├── AttendanceService.java            (interface)
│       └── AttendanceServiceImpl.java
├── main/resources/db/migration/
│   └── V3__create_attendance_records.sql
└── test/java/com/example/attendance/attendance/
    ├── controller/AttendanceControllerTest.java
    ├── domain/WorkDurationTest.java
    ├── repository/AttendanceRecordRepositoryTest.java
    └── service/AttendanceServiceTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/attendance/
├── ClockButtons.tsx
├── TodayRecords.tsx
├── AttendanceTable.tsx
├── MonthlySummary.tsx
├── TeamAttendanceTable.tsx
├── AllAttendanceTable.tsx
├── useAttendance.ts
└── attendance-api.ts

packages/frontend/src/app/(authenticated)/
├── page.tsx                    (ダッシュボード)
├── history/page.tsx
├── team/page.tsx
└── admin/attendance/page.tsx
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| WorkDuration: 6h以下 | Unit | 休憩0分、残業0分 |
| WorkDuration: 6h超〜8h以下 | Unit | 休憩45分 |
| WorkDuration: 8h超 | Unit | 休憩60分、残業あり |
| WorkDuration: 複数レコード合算 | Unit | Σ(clockOut-clockIn) を正しく計算 |
| Repository: 社員×日付で検索 | DataJpaTest | employee_id + work_date で取得 |
| Repository: 月指定で検索 | DataJpaTest | 月の範囲で取得 |
| Service: 出勤打刻（正常） | Unit | レコード作成、clockOut=null |
| Service: 出勤打刻（未退勤あり） | Unit | 409 例外 (ATT-04) |
| Service: 退勤打刻（正常） | Unit | clockOut が設定される |
| Service: 退勤打刻（出勤中なし） | Unit | 409 例外 (ATT-05) |
| Service: 再出勤（退勤後） | Unit | 新レコード作成 (ATT-06) |
| Service: 当日ステータス（未出勤） | Unit | NOT_CLOCKED_IN |
| Service: 当日ステータス（出勤中） | Unit | CLOCKED_IN |
| Service: 当日ステータス（退勤済み） | Unit | CLOCKED_OUT |
| Service: 勤怠履歴（月指定） | Unit | 日別レコード + 勤務時間計算 |
| Service: チーム勤怠 | Unit | 自部署メンバーのサマリー |
| Service: 全社員勤怠 | Unit | 全社員 or 部署フィルタ |
| Controller: POST clock-in | WebMvcTest | 201 + レコード |
| Controller: POST clock-out | WebMvcTest | 200 + レコード |
| Controller: GET today | WebMvcTest | 200 + ステータス |
| Controller: GET history | WebMvcTest | 200 + 月別履歴 |
| Controller: GET team（上長） | WebMvcTest | 200 |
| Controller: GET team（一般） | WebMvcTest | 403 |
| Controller: GET all（管理者） | WebMvcTest | 200 |
| Controller: GET all（一般） | WebMvcTest | 403 |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| ClockButtons: 未出勤→出勤のみ活性 | Component | 出勤ボタンが活性、退勤が非活性 |
| ClockButtons: 出勤中→退勤のみ活性 | Component | 退勤ボタンが活性 |
| ClockButtons: 退勤済み→再出勤可能 | Component | 出勤ボタンが活性 |
| ClockButtons: 打刻後に状態更新 | Component | API 呼び出し後にステータス変化 |
| TodayRecords: レコード一覧表示 | Component | 打刻記録をリスト表示 |
| AttendanceTable: 月別表示 | Component | 日別に勤務時間を表示 |
| AttendanceTable: 修正済みマーク | Component | corrected=true にマーク表示 |
| MonthlySummary: 集計表示 | Component | 出勤日数・勤務時間等を表示 |
| MonthSelector: 月切り替え | Component | 前月・翌月で再取得 |

## ビジネスルール

- 打刻はサーバー時刻（`Instant.now()`）で記録
- 同一社員・同一日に複数レコード可能（出勤→退勤→出勤→退勤）
- `clockOut = null` が存在 → 出勤打刻不可（ATT-04）
- `clockOut = null` が不在 → 退勤打刻不可（ATT-05）
- 勤務時間計算: 各レコードの `(clockOut - clockIn)` を合算（分単位）
- 休憩控除: 合算勤務時間が 6h超〜8h以下 → 45分、8h超 → 60分、6h以下 → 0分
- 残業: max(0, 実勤務時間 - 480分)

## 完了条件

- [ ] 出勤・退勤打刻が正しく動作する
- [ ] 打刻の排他制御（二重出勤不可、未出勤退勤不可）が機能する
- [ ] 退勤後の再出勤が可能
- [ ] 勤務時間・休憩控除・残業時間が正しく計算される
- [ ] 月別勤怠履歴が表示される
- [ ] 上長がチーム勤怠を確認できる
- [ ] 管理者が全社員勤怠を確認できる
- [ ] フロントエンドの打刻ボタンがステータスに応じて適切に動作する
- [ ] Backend テストカバレッジ 80% 以上（attendance パッケージ）
