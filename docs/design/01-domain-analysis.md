# ドメイン分析

要件定義と設計判断（`docs/working/design/01-domain-analysis.md`）をもとに整理。

## ドメイン一覧

| ドメイン | 責務 | 主な Entity |
|---------|------|------------|
| auth | 認証（ログイン/ログアウト） | — (Spring Security で処理) |
| employee | 社員マスタの CRUD | Employee |
| department | 部署マスタの CRUD | Department |
| attendance | 出退勤の打刻・勤怠履歴 | AttendanceRecord |
| correction | 勤怠修正の申請・承認 | AttendanceCorrection |
| report | 月次集計・帳票出力 | — (attendance, employee のデータを集計) |

---

## Entity 定義

### Employee（社員）

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | 時系列ソート可能 |
| name | String | NOT NULL | 氏名 |
| email | String | NOT NULL, UNIQUE | ログイン ID |
| password | String | NOT NULL | BCrypt ハッシュ |
| department | Department | FK, NOT NULL | 所属部署 |
| role | Role (enum) | NOT NULL | EMPLOYEE / ADMIN |
| isManager | boolean | NOT NULL, default false | 部署の上長フラグ（1部署1人） |
| hireDate | LocalDate | NOT NULL | 入社日 |
| retireDate | LocalDate | nullable | null = 在籍中。設定するとログイン不可 |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | 作成日時 (UTC) |
| updatedAt | Instant | NOT NULL | 更新日時 (UTC) |

ビジネスルール:
- `retireDate` が設定されている社員はログイン不可（AUTH-03）
- `isManager = true` の社員は所属部署の修正申請を承認できる
- `role = ADMIN` と `isManager = true` は兼任可能
- 1 部署につき `isManager = true` は 1 人のみ

### Department（部署）

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | |
| name | String | NOT NULL, UNIQUE | 部署名 |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | |
| updatedAt | Instant | NOT NULL | |

ビジネスルール:
- フラット構成（親子関係なし）
- 部署に所属する社員がいる場合、削除不可

### AttendanceRecord（打刻記録）

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | |
| employee | Employee | FK, NOT NULL | 打刻した社員 |
| workDate | LocalDate | NOT NULL | 勤務日 |
| clockIn | Instant | NOT NULL | 出勤時刻 (UTC) |
| clockOut | Instant | nullable | 退勤時刻 (UTC)。null = 出勤中 |
| corrected | boolean | NOT NULL, default false | 修正申請で変更されたか |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | |
| updatedAt | Instant | NOT NULL | |

ビジネスルール:
- 同一社員・同一日に複数レコード可能（ATT-06: 出勤→退勤→出勤→退勤）
- `clockOut = null` のレコードがある場合、新たに出勤打刻は不可（ATT-04）
- `clockOut = null` のレコードがない場合、退勤打刻は不可（ATT-05）
- 勤務時間計算: 各レコードの `(clockOut - clockIn)` を合算（CALC-01）
- 休憩控除は合算勤務時間に対して適用（CALC-02）

### AttendanceCorrection（勤怠修正申請）

| フィールド | 型 | 制約 | 備考 |
|-----------|-----|------|------|
| id | UUID (v7) | PK | |
| attendanceRecord | AttendanceRecord | FK, nullable | 既存レコードの修正時に設定。打刻忘れの場合は null |
| requester | Employee | FK, NOT NULL | 申請者 |
| approver | Employee | FK, nullable | 承認/却下した上長。処理前は null |
| targetDate | LocalDate | NOT NULL | 修正対象日 |
| correctedClockIn | Instant | NOT NULL | 修正後の出勤時刻 |
| correctedClockOut | Instant | NOT NULL | 修正後の退勤時刻 |
| reason | String | NOT NULL | 修正理由 |
| status | CorrectionStatus (enum) | NOT NULL | PENDING / APPROVED / REJECTED |
| version | Long | @Version | 楽観ロック |
| createdAt | Instant | NOT NULL | |
| updatedAt | Instant | NOT NULL | |

ビジネスルール:
- `attendanceRecord = null` の場合、承認時に新規 AttendanceRecord を作成
- `attendanceRecord != null` の場合、承認時に既存レコードの `clockIn`/`clockOut` を更新し `corrected = true` に設定
- 承認者は申請者の所属部署の上長（FIX-05）
- 上長が自分の修正を申請した場合は自己承認（補足F）
- 承認/却下操作と取り下げ操作の競合は楽観ロックで制御（FIX-07）

---

## Enum 定義

### Role（ロール）

```
EMPLOYEE  — 一般社員
ADMIN     — 管理者（人事部門）
```

「上長」はロールではなく、Employee の `isManager` フラグで管理する。

### CorrectionStatus（修正申請ステータス）

```
PENDING   — 申請中（上長の承認待ち）
APPROVED  — 承認済み（勤怠データに反映済み）
REJECTED  — 却下
```

---

## Value Object

### WorkDuration（勤務時間）

ある日の勤務時間計算結果を表す Value Object。Entity ではなく、計算結果を返す際に使用する。

| フィールド | 型 | 説明 |
|-----------|-----|------|
| totalMinutes | int | 合算勤務時間（分）: Σ(clockOut - clockIn) |
| breakMinutes | int | 休憩控除（分）: 6h超〜8h以下→45, 8h超→60, 6h以下→0 |
| workMinutes | int | 実勤務時間（分）: totalMinutes - breakMinutes |
| overtimeMinutes | int | 残業時間（分）: max(0, workMinutes - 480) |

---

## ドメイン関連図

```
┌──────────────┐       ┌──────────────────┐
│  Department  │1    N │    Employee       │
│              │───────│                   │
│  name        │       │  name             │
│              │       │  email            │
└──────────────┘       │  role (enum)      │
                       │  isManager (flag) │
                       │  hireDate         │
                       │  retireDate       │
                       └──────┬──────┬─────┘
                              │1     │1
                              │      │
                           N  │      │  N
               ┌──────────────┘      └───────────────┐
               │                                      │
    ┌──────────▼─────────┐          ┌─────────────────▼────────────┐
    │ AttendanceRecord   │          │  AttendanceCorrection        │
    │                    │0..1    N │                              │
    │  workDate          │◄─────────│  targetDate                  │
    │  clockIn           │          │  correctedClockIn            │
    │  clockOut          │          │  correctedClockOut           │
    │  corrected         │          │  reason                      │
    │                    │          │  status (enum)               │
    └────────────────────┘          │  approver → Employee         │
                                    └──────────────────────────────┘
```

関連:
- Department `1 : N` Employee — 1部署に複数社員が所属
- Employee `1 : N` AttendanceRecord — 1社員が複数の打刻記録を持つ
- Employee `1 : N` AttendanceCorrection — 1社員（requester）が複数の修正申請を出す
- AttendanceRecord `0..1 : N` AttendanceCorrection — 1つの打刻記録に対して複数の修正申請がありうる（却下→再申請）。打刻忘れの場合は紐づかない (null)
- Employee → AttendanceCorrection (approver) — 承認者としての参照

---

## パッケージ構成

```
com.example.attendance
├── auth/              — 認証（ログイン/ログアウト）
│   ├── controller/
│   ├── service/
│   └── dto/
├── employee/          — 社員マスタ管理
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── department/        — 部署管理
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── attendance/        — 打刻・勤怠履歴
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── correction/        — 勤怠修正（申請・承認）
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── report/            — 月次集計・帳票出力
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/            — 共通基盤
    ├── config/        — SecurityFilterChain, CORS, Actuator, etc.
    ├── exception/     — @RestControllerAdvice
    ├── logging/       — 構造化ログ設定（JSON Logback）
    └── util/          — 共通ユーティリティ
```

ドメイン間の依存方向:
- `auth` → `employee`（認証時にユーザー情報を参照）
- `attendance` → `employee`（打刻時に社員を参照）
- `correction` → `employee`, `attendance`（修正申請・承認で両方参照）
- `report` → `employee`, `attendance`（集計で両方参照）
- `common` ← 全ドメインから参照される

---

## 運用監視（AI エージェント対応）

AI エージェントが自律的にアプリケーション状態をチェックできるよう、以下を整備する。

### 構造化ログ

- Logback で JSON 形式のログを出力する（Logstash Logback Encoder）
- AI エージェントはログファイルや CloudWatch Logs を CLI で直接読み取れる
- ログに含めるフィールド: `timestamp`, `level`, `logger`, `message`, `traceId`, `employeeId`（該当する場合）

### リクエストトレーシング

- Micrometer Tracing を導入し、HTTP リクエストごとに `traceId` を自動付与する
- 1つのリクエストに対する全ログ（Controller → Service → Repository）が同じ `traceId` を持つ
- エラー発生時、AI エージェントが `traceId` で CloudWatch Logs を検索して原因を遡れる

```bash
# エラーログを探す
aws logs filter-log-events \
  --log-group-name /app/attendance \
  --filter-pattern '"level":"ERROR"'

# 見つかった traceId で前後のログを追う
aws logs filter-log-events \
  --log-group-name /app/attendance \
  --filter-pattern '"traceId":"abc123"'
```

### Spring Boot Actuator

- `/actuator/health` — アプリ・DB の死活監視
- `/actuator/info` — ビルド情報・バージョン
- `/actuator/metrics` — JVM・HTTP リクエスト等のメトリクス
- Actuator エンドポイントは認証不要（ネットワーク制限で保護）

`common/config/` に Actuator 設定、`common/logging/` に Logback JSON 設定を配置する。

---

## 設計判断まとめ

| 項目 | 決定 | 根拠 |
|------|------|------|
| 打刻レコード粒度 | 1出退勤ペア = 1レコード | シンプル。GROUP BY で日次集計可 |
| 打刻忘れの修正 | 修正申請で新規レコードも作成可能 | 同一フローで統一 |
| 修正申請の構造 | 修正後の値を保持 | 承認時の処理がシンプル |
| パッケージ構成 | ドメイン分割レイヤード | モダン Java デモとして適切 |
| タイムゾーン | UTC 保存・JST 表示 | Instant で管理 |
| 社員 ID | UUID v7 (uuid-creator) | コンストラクタ生成。persist 前に ID 確定 |
