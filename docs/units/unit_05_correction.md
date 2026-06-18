# Unit 05: 勤怠修正

勤怠修正の申請・承認・却下。打刻忘れの新規作成にも対応。

## 依存関係

- 依存先: Unit 00（共通基盤）, Unit 02（社員 — 申請者・承認者）, Unit 03（認証）, Unit 04（打刻 — AttendanceRecord 参照・更新）
- 依存元: なし
- **Unit 06（集計）とは並列実装可能**

## ユーザーストーリー

- **FIX-01**: 社員として、既存の打刻記録を修正申請したい
- **FIX-02**: 社員として、打刻忘れ分の新規レコードを修正申請したい
- **FIX-03**: 社員として、自分の修正申請の状態を確認したい
- **FIX-04**: 上長として、部署メンバーの修正申請を承認・却下したい
- **FIX-05**: 承認者は申請者の所属部署の上長に限定する
- **FIX-06**: 上長自身の修正申請は自己承認する
- **FIX-07**: 承認/却下と取り下げの競合は楽観ロックで制御する

## テーブル

### attendance_corrections

```sql
CREATE TABLE attendance_corrections (
    id UUID PRIMARY KEY,
    attendance_record_id UUID REFERENCES attendance_records(id),
    requester_id UUID NOT NULL REFERENCES employees(id),
    approver_id UUID REFERENCES employees(id),
    target_date DATE NOT NULL,
    corrected_clock_in TIMESTAMPTZ NOT NULL,
    corrected_clock_out TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attendance_corrections_requester ON attendance_corrections(requester_id);
CREATE INDEX idx_attendance_corrections_status ON attendance_corrections(status);
```

Flyway: `V4__create_attendance_corrections.sql`

## API

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| POST | `/api/corrections` | 修正申請 | 全ロール |
| GET | `/api/corrections` | 自分の申請一覧 | 全ロール |
| GET | `/api/corrections/pending` | 承認待ち一覧 | 上長 |
| PATCH | `/api/corrections/{id}/approve` | 承認 | 上長 |
| PATCH | `/api/corrections/{id}/reject` | 却下 | 上長 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/corrections` | 修正申請一覧 | `CorrectionList`, `StatusFilter`, `StatusBadge` |
| `/corrections/new` | 修正申請作成 | `CorrectionForm` |
| `/approvals` | 承認待ち一覧 | `PendingCorrectionList`, `ApprovalActions` |

## Backend 実装順序（TDD）

1. Flyway マイグレーション `V4__create_attendance_corrections.sql`
2. `CorrectionStatus` Enum
3. `AttendanceCorrection` Entity
4. `AttendanceCorrectionRepository` テスト → 実装
5. `CorrectionService` テスト → interface → 実装
6. `CorrectionController` テスト → 実装
7. 統合テスト（承認フロー全体）

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/correction/
│   ├── controller/CorrectionController.java
│   ├── dto/
│   │   ├── CorrectionCreateRequest.java      (record)
│   │   ├── CorrectionRejectRequest.java      (record)
│   │   ├── CorrectionResponse.java           (record)
│   │   └── PendingCorrectionResponse.java    (record)
│   ├── entity/
│   │   ├── AttendanceCorrection.java
│   │   └── CorrectionStatus.java             (enum)
│   ├── repository/AttendanceCorrectionRepository.java
│   └── service/
│       ├── CorrectionService.java            (interface)
│       └── CorrectionServiceImpl.java
├── main/resources/db/migration/
│   └── V4__create_attendance_corrections.sql
└── test/java/com/example/attendance/correction/
    ├── controller/CorrectionControllerTest.java
    ├── repository/AttendanceCorrectionRepositoryTest.java
    └── service/CorrectionServiceTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/correction/
├── CorrectionForm.tsx
├── CorrectionList.tsx
├── PendingCorrectionList.tsx
├── ApprovalActions.tsx
├── StatusFilter.tsx
├── useCorrections.ts
└── correction-api.ts

packages/frontend/src/app/(authenticated)/
├── corrections/
│   ├── page.tsx
│   └── new/page.tsx
└── approvals/
    └── page.tsx
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| Repository: 申請者で検索 | DataJpaTest | requester_id で一覧取得 |
| Repository: ステータスで検索 | DataJpaTest | PENDING で絞り込み |
| Service: 修正申請（既存レコード） | Unit | attendanceRecordId を設定して作成 |
| Service: 修正申請（打刻忘れ） | Unit | attendanceRecordId = null で作成 |
| Service: 自分の申請一覧 | Unit | ログインユーザーの申請のみ返す |
| Service: 自分の申請一覧（ステータスフィルタ） | Unit | PENDING のみ返す |
| Service: 承認待ち一覧 | Unit | 自部署メンバーの PENDING のみ |
| Service: 承認（既存レコード修正） | Unit | AttendanceRecord の clockIn/clockOut 更新、corrected=true |
| Service: 承認（打刻忘れ新規） | Unit | 新規 AttendanceRecord 作成、corrected=true |
| Service: 承認（承認者が部署の上長） | Unit | 正常完了 |
| Service: 承認（承認者が上長でない） | Unit | 403 相当の例外 |
| Service: 上長の自己承認 | Unit | 自分自身が承認者として処理される (FIX-06) |
| Service: 却下 | Unit | status=REJECTED、理由が保存される |
| Service: 承認（楽観ロックエラー） | Unit | OptimisticLockException (FIX-07) |
| Controller: POST /api/corrections | WebMvcTest | 201 |
| Controller: GET /api/corrections | WebMvcTest | 200 + 申請一覧 |
| Controller: GET /api/corrections/pending（上長） | WebMvcTest | 200 |
| Controller: GET /api/corrections/pending（一般） | WebMvcTest | 403 |
| Controller: PATCH approve | WebMvcTest | 200 |
| Controller: PATCH reject | WebMvcTest | 200 |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| CorrectionForm: 対象日選択→レコード取得 | Component | 日付選択で既存レコードが選択肢に |
| CorrectionForm: 既存レコード選択→プリフィル | Component | 現在の値が入力欄にセット |
| CorrectionForm: バリデーション | Component | 出勤 < 退勤、理由必須 |
| CorrectionForm: 申請送信 | Component | API 呼び出し → 一覧へ遷移 |
| CorrectionList: ステータスフィルタ | Component | PENDING/APPROVED/REJECTED 切り替え |
| PendingCorrectionList: 承認ボタン | Component | 承認 → ステータス変化 |
| PendingCorrectionList: 却下（理由入力） | Component | 却下ダイアログ → 理由入力 → 送信 |
| ApprovalActions: 楽観ロックエラー | Component | トースト通知表示 |

## ビジネスルール

- `attendanceRecordId = null` → 打刻忘れ。承認時に新規 AttendanceRecord を作成
- `attendanceRecordId != null` → 既存レコード修正。承認時に clockIn/clockOut を更新し corrected=true
- 承認者は申請者の所属部署の上長（`isManager = true`）
- 上長自身の修正申請は自己承認
- 楽観ロック: 承認/却下操作時に version チェック

## 完了条件

- [ ] 既存レコードの修正申請ができる
- [ ] 打刻忘れの修正申請ができる
- [ ] 上長が承認待ち一覧を確認できる
- [ ] 承認すると AttendanceRecord が更新（or 新規作成）される
- [ ] 却下すると理由が保存される
- [ ] 他部署の申請は承認できない
- [ ] 上長の自己承認が動作する
- [ ] 楽観ロックエラーが適切に処理される
- [ ] フロントエンドの修正申請・承認フローが一通り動作する
- [ ] Backend テストカバレッジ 80% 以上（correction パッケージ）
