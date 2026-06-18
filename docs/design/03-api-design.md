# API 設計

設計判断（`docs/working/design/03-api-design.md`）をもとに整理。

## 共通仕様

| 項目 | 内容 |
|------|------|
| ベースパス | `/api/` |
| 認証 | セッションベース（Spring Security）。Cookie でセッション ID を管理 |
| リクエスト形式 | `application/json` |
| レスポンス形式 | `application/json`（帳票は `text/csv`, `application/pdf`） |
| エラー形式 | RFC 9457 Problem Details (`application/problem+json`) |
| ページネーション | オフセットベース `?page=0&size=20` |
| 日時形式 | ISO 8601（`2026-06-16T09:00:00Z`） |
| OpenAPI | SpringDoc で自動生成。Swagger UI で確認可能 |

### ページネーションレスポンス

```json
{
  "content": [...],
  "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
}
```

### エラーレスポンス（RFC 9457）

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "出勤済みのため、再度出勤打刻はできません",
  "instance": "/api/attendance/clock-in"
}
```

バリデーションエラー時は `errors` フィールドを追加:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "入力内容に誤りがあります",
  "instance": "/api/employees",
  "errors": [
    { "field": "email", "message": "メールアドレスの形式が正しくありません" }
  ]
}
```

---

## 認証 (auth)

| メソッド | パス | 説明 | 認証 |
|---------|------|------|------|
| POST | `/api/auth/login` | ログイン | 不要 |
| POST | `/api/auth/logout` | ログアウト | 必要 |
| GET | `/api/auth/me` | ログイン中のユーザー情報取得 | 必要 |

### POST /api/auth/login

ログイン。成功するとセッション Cookie が発行される。

Request:
```json
{
  "email": "tanaka@example.com",
  "password": "password123"
}
```

Response (200):
```json
{
  "id": "019059a1-...",
  "name": "田中太郎",
  "email": "tanaka@example.com",
  "departmentId": "019059a1-...",
  "departmentName": "開発部",
  "role": "EMPLOYEE",
  "isManager": false
}
```

Error (401): メールアドレスまたはパスワードが正しくない / 退職済み

### POST /api/auth/logout

ログアウト。セッションを破棄する。

Response (204): No Content

### GET /api/auth/me

現在ログイン中のユーザー情報を返す。フロントエンドの初期化時に使用。

Response (200): ログインレスポンスと同じ形式

Error (401): 未認証

---

## 部署管理 (department)

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/departments` | 部署一覧 | 全ロール |
| POST | `/api/departments` | 部署登録 | 管理者 |
| PUT | `/api/departments/{id}` | 部署編集 | 管理者 |

### GET /api/departments

部署一覧。社員登録時の部署選択等で使用。ページネーションなし（件数が少ないため）。

Response (200):
```json
[
  { "id": "019059a1-...", "name": "人事部" },
  { "id": "019059a2-...", "name": "開発部" },
  { "id": "019059a3-...", "name": "営業部" }
]
```

### POST /api/departments

Request:
```json
{ "name": "総務部" }
```

Response (201):
```json
{ "id": "019059a4-...", "name": "総務部" }
```

Error (409): 同名の部署が既に存在

### PUT /api/departments/{id}

Request:
```json
{ "name": "開発部（改名後）" }
```

Response (200): 更新後の部署情報

Error (404): 部署が見つからない / (409): 同名の部署が既に存在

---

## 社員管理 (employee)

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/employees` | 社員一覧 | 管理者 |
| GET | `/api/employees/{id}` | 社員詳細 | 管理者 |
| POST | `/api/employees` | 社員登録 | 管理者 |
| PUT | `/api/employees/{id}` | 社員編集 | 管理者 |
| PATCH | `/api/employees/{id}/retire` | 退職処理 | 管理者 |
| PATCH | `/api/employees/{id}/manager` | 上長設定 | 管理者 |

### GET /api/employees

社員一覧。フィルタ・ページネーション対応。

Query Parameters:
- `departmentId` (任意): 部署で絞り込み
- `role` (任意): ロールで絞り込み (`EMPLOYEE` / `ADMIN`)
- `includeRetired` (任意, default: false): 退職者を含むか
- `page` (default: 0)
- `size` (default: 20)

Response (200):
```json
{
  "content": [
    {
      "id": "019059a1-...",
      "name": "田中太郎",
      "email": "tanaka@example.com",
      "departmentId": "019059a1-...",
      "departmentName": "開発部",
      "role": "EMPLOYEE",
      "isManager": false,
      "hireDate": "2024-04-01",
      "retireDate": null
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 12, "totalPages": 1 }
}
```

### GET /api/employees/{id}

社員詳細。退職社員の情報も参照可能（EMP-06）。

Response (200): 社員一覧の 1 要素と同じ形式

### POST /api/employees

社員を新規登録する。

Request:
```json
{
  "name": "佐藤花子",
  "email": "sato@example.com",
  "password": "initialPass123",
  "departmentId": "019059a2-...",
  "role": "EMPLOYEE",
  "hireDate": "2026-04-01"
}
```

Response (201): 登録された社員情報（password は含まない）

Error (409): メールアドレスが重複

### PUT /api/employees/{id}

社員情報を編集する。パスワードはこの API では変更しない（スコープ外）。

Request:
```json
{
  "name": "佐藤花子",
  "email": "sato@example.com",
  "departmentId": "019059a2-...",
  "role": "ADMIN",
  "hireDate": "2026-04-01"
}
```

Response (200): 更新後の社員情報

### PATCH /api/employees/{id}/retire

退職処理。退職日を設定する。

Request:
```json
{ "retireDate": "2026-06-30" }
```

Response (200): 更新後の社員情報

### PATCH /api/employees/{id}/manager

上長フラグを設定/解除する。設定時に同部署に既に上長がいればエラー。

Request:
```json
{ "isManager": true }
```

Response (200): 更新後の社員情報

Error (409): 同部署に既に上長がいる

---

## 打刻・勤怠履歴 (attendance)

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| POST | `/api/attendance/clock-in` | 出勤打刻 | 全ロール |
| POST | `/api/attendance/clock-out` | 退勤打刻 | 全ロール |
| GET | `/api/attendance/today` | 当日の打刻状態 | 全ロール |
| GET | `/api/attendance/history` | 自分の勤怠履歴 | 全ロール |
| GET | `/api/attendance/team` | 自部署メンバーの勤怠 | 上長 |
| GET | `/api/attendance/all` | 全社員の勤怠 | 管理者 |

### POST /api/attendance/clock-in

出勤打刻。サーバー時刻で記録する。

Request: ボディなし

Response (201):
```json
{
  "id": "019059b1-...",
  "workDate": "2026-06-16",
  "clockIn": "2026-06-16T00:00:00Z",
  "clockOut": null,
  "corrected": false
}
```

Error (409): 出勤中（未退勤）のレコードが既にある（ATT-04）

### POST /api/attendance/clock-out

退勤打刻。出勤中のレコードの `clockOut` を更新する。

Request: ボディなし

Response (200):
```json
{
  "id": "019059b1-...",
  "workDate": "2026-06-16",
  "clockIn": "2026-06-16T00:00:00Z",
  "clockOut": "2026-06-16T09:00:00Z",
  "corrected": false
}
```

Error (409): 出勤中のレコードがない（ATT-05）

### GET /api/attendance/today

当日の打刻状態を返す。フロントエンドの打刻ボタン制御に使用。

Response (200):
```json
{
  "status": "CLOCKED_IN",
  "records": [
    {
      "id": "019059b1-...",
      "clockIn": "2026-06-16T00:00:00Z",
      "clockOut": null,
      "corrected": false
    }
  ]
}
```

`status` の値:
- `NOT_CLOCKED_IN`: 未出勤（出勤ボタン表示）
- `CLOCKED_IN`: 出勤中（退勤ボタン表示）
- `CLOCKED_OUT`: 退勤済み（再出勤ボタン表示）

### GET /api/attendance/history

自分の勤怠履歴。月指定で取得。

Query Parameters:
- `month` (必須): `2026-06` 形式

Response (200):
```json
{
  "month": "2026-06",
  "days": [
    {
      "date": "2026-06-16",
      "records": [
        {
          "id": "019059b1-...",
          "clockIn": "2026-06-16T00:00:00Z",
          "clockOut": "2026-06-16T09:00:00Z",
          "corrected": false
        }
      ],
      "totalWorkMinutes": 480,
      "breakMinutes": 60,
      "workMinutes": 420,
      "overtimeMinutes": 0
    }
  ],
  "summary": {
    "workDays": 15,
    "totalWorkMinutes": 7200,
    "totalOvertimeMinutes": 120,
    "absentDays": 1
  }
}
```

### GET /api/attendance/team

自部署メンバーの勤怠。上長向け。

Query Parameters:
- `month` (必須): `2026-06` 形式

Response (200):
```json
{
  "month": "2026-06",
  "members": [
    {
      "employeeId": "019059a1-...",
      "employeeName": "田中太郎",
      "workDays": 15,
      "totalWorkMinutes": 7200,
      "totalOvertimeMinutes": 120,
      "absentDays": 1
    }
  ]
}
```

### GET /api/attendance/all

全社員の勤怠。管理者向け。`/team` と同じ形式だが全社員分。

Query Parameters:
- `month` (必須): `2026-06` 形式
- `departmentId` (任意): 部署で絞り込み

Response (200): `/team` と同じ形式

---

## 勤怠修正 (correction)

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| POST | `/api/corrections` | 修正申請 | 全ロール |
| GET | `/api/corrections` | 自分の申請一覧 | 全ロール |
| GET | `/api/corrections/pending` | 承認待ち一覧 | 上長 |
| PATCH | `/api/corrections/{id}/approve` | 承認 | 上長 |
| PATCH | `/api/corrections/{id}/reject` | 却下 | 上長 |

### POST /api/corrections

勤怠修正を申請する。既存レコードの修正も、打刻忘れの新規作成も同じエンドポイント。

Request:
```json
{
  "attendanceRecordId": "019059b1-...",
  "targetDate": "2026-06-15",
  "correctedClockIn": "2026-06-15T00:00:00Z",
  "correctedClockOut": "2026-06-15T09:00:00Z",
  "reason": "打刻忘れのため"
}
```

- `attendanceRecordId`: 既存レコードの修正時に指定。打刻忘れの場合は `null`

Response (201):
```json
{
  "id": "019059c1-...",
  "attendanceRecordId": "019059b1-...",
  "targetDate": "2026-06-15",
  "correctedClockIn": "2026-06-15T00:00:00Z",
  "correctedClockOut": "2026-06-15T09:00:00Z",
  "reason": "打刻忘れのため",
  "status": "PENDING",
  "createdAt": "2026-06-16T02:00:00Z"
}
```

### GET /api/corrections

自分の修正申請一覧。

Query Parameters:
- `status` (任意): `PENDING` / `APPROVED` / `REJECTED` で絞り込み

Response (200):
```json
[
  {
    "id": "019059c1-...",
    "attendanceRecordId": "019059b1-...",
    "targetDate": "2026-06-15",
    "correctedClockIn": "2026-06-15T00:00:00Z",
    "correctedClockOut": "2026-06-15T09:00:00Z",
    "reason": "打刻忘れのため",
    "status": "PENDING",
    "approverName": null,
    "createdAt": "2026-06-16T02:00:00Z"
  }
]
```

### GET /api/corrections/pending

自部署メンバーの承認待ち修正申請一覧。上長向け。

Response (200):
```json
[
  {
    "id": "019059c1-...",
    "requesterId": "019059a1-...",
    "requesterName": "田中太郎",
    "targetDate": "2026-06-15",
    "correctedClockIn": "2026-06-15T00:00:00Z",
    "correctedClockOut": "2026-06-15T09:00:00Z",
    "reason": "打刻忘れのため",
    "status": "PENDING",
    "createdAt": "2026-06-16T02:00:00Z"
  }
]
```

### PATCH /api/corrections/{id}/approve

修正申請を承認する。承認時に打刻レコードを更新（or 新規作成）し、`corrected = true` にする。

Request: ボディなし

Response (200): 更新後の修正申請情報（`status: "APPROVED"`, `approverName` が設定される）

Error (409): 楽観ロックエラー（既に取り下げ済み等） / (404): 申請が見つからない

### PATCH /api/corrections/{id}/reject

修正申請を却下する。

Request:
```json
{ "reason": "打刻時刻が不正です" }
```

Response (200): 更新後の修正申請情報（`status: "REJECTED"`）

Error (409): 楽観ロックエラー

---

## 月次集計・帳票 (report)

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/reports/monthly` | 月次集計（画面表示用） | 管理者 |
| GET | `/api/reports/monthly/csv` | CSV エクスポート | 管理者 |
| GET | `/api/reports/monthly/pdf` | PDF 帳票出力 | 管理者 |

### GET /api/reports/monthly

月次集計データを JSON で返す。

Query Parameters:
- `month` (必須): `2026-06` 形式
- `departmentId` (任意): 部署で絞り込み

Response (200):
```json
{
  "month": "2026-06",
  "records": [
    {
      "employeeId": "019059a1-...",
      "employeeName": "田中太郎",
      "departmentName": "開発部",
      "workDays": 20,
      "totalWorkMinutes": 9600,
      "totalOvertimeMinutes": 480,
      "absentDays": 2
    }
  ]
}
```

### GET /api/reports/monthly/csv

月次集計を CSV でダウンロードする。

Query Parameters: `/monthly` と同じ

Response (200): `Content-Type: text/csv`, `Content-Disposition: attachment; filename="monthly-report-2026-06.csv"`

CSV カラム: 社員名, 所属部署, 出勤日数, 勤務時間合計, 残業時間合計, 欠勤日数

### GET /api/reports/monthly/pdf

月次集計を PDF 帳票として出力する（JasperReports）。

Query Parameters: `/monthly` と同じ

Response (200): `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="monthly-report-2026-06.pdf"`

---

## 運用監視 (Actuator)

Spring Boot Actuator のエンドポイント。AI エージェントがアプリの状態を自律的にチェックするために公開する。

| パス | 説明 | 認証 |
|------|------|------|
| `GET /actuator/health` | アプリ・DB の死活監視 | 不要 |
| `GET /actuator/info` | ビルド情報・バージョン | 不要 |
| `GET /actuator/metrics` | メトリクス一覧 | 不要 |
| `GET /actuator/metrics/{name}` | 個別メトリクス（例: `http.server.requests`） | 不要 |

Actuator はアプリ API（`/api/`）とは別パス。本番ではネットワーク制限（セキュリティグループ等）で保護する。

### GET /actuator/health

Response (200):
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "diskSpace": { "status": "UP" }
  }
}
```

### ログ形式（構造化 JSON）

Logback + Logstash Logback Encoder で JSON 形式のログを出力する。AI エージェントは `grep` / `jq` / `aws logs` 等で直接パースできる。

```json
{
  "timestamp": "2026-06-17T01:00:00.000Z",
  "level": "ERROR",
  "logger": "com.example.attendance.attendance.service.AttendanceServiceImpl",
  "message": "打刻失敗: 既に出勤済み",
  "employeeId": "019059a1-...",
  "traceId": "abc123",
  "stackTrace": "..."
}
```

---

## エンドポイント × 権限マトリクス

| エンドポイント | 未認証 | 一般社員 | 上長 | 管理者 |
|--------------|--------|---------|------|--------|
| POST /api/auth/login | o | - | - | - |
| POST /api/auth/logout | - | o | o | o |
| GET /api/auth/me | - | o | o | o |
| GET /api/departments | - | o | o | o |
| POST /api/departments | - | - | - | o |
| PUT /api/departments/{id} | - | - | - | o |
| GET /api/employees | - | - | - | o |
| GET /api/employees/{id} | - | - | - | o |
| POST /api/employees | - | - | - | o |
| PUT /api/employees/{id} | - | - | - | o |
| PATCH /api/employees/{id}/retire | - | - | - | o |
| PATCH /api/employees/{id}/manager | - | - | - | o |
| POST /api/attendance/clock-in | - | o | o | o |
| POST /api/attendance/clock-out | - | o | o | o |
| GET /api/attendance/today | - | o | o | o |
| GET /api/attendance/history | - | o | o | o |
| GET /api/attendance/team | - | - | o | - |
| GET /api/attendance/all | - | - | - | o |
| POST /api/corrections | - | o | o | o |
| GET /api/corrections | - | o | o | o |
| GET /api/corrections/pending | - | - | o | - |
| PATCH /api/corrections/{id}/approve | - | - | o | - |
| PATCH /api/corrections/{id}/reject | - | - | o | - |
| GET /api/reports/monthly | - | - | - | o |
| GET /api/reports/monthly/csv | - | - | - | o |
| GET /api/reports/monthly/pdf | - | - | - | o |

- `o` = アクセス可能
- `-` = アクセス不可（未認証 → 401、権限不足 → 403）
