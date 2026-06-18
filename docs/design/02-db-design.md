# DB 設計

ドメイン分析（`01-domain-analysis.md`）と設計判断（`docs/working/design/02-db-design.md`）をもとに整理。

## 設計方針

| 項目 | 決定 |
|------|------|
| テーブル名・カラム名 | snake_case、複数形 |
| PK | UUID v7（PostgreSQL `uuid` 型） |
| Enum | `varchar` + CHECK 制約 |
| 楽観ロック | `version` カラム（`bigint`） |
| 監査カラム | `created_at` / `updated_at`（Spring Data Auditing） |
| タイムゾーン | `timestamptz`（UTC 保存、アプリで JST 表示） |
| 初期データ | Flyway バージョンマイグレーション |
| 論理削除 | しない（退職は `retire_date` で表現） |

---

## テーブル定義

### departments（部署）

| カラム | 型 | 制約 | 説明 |
|--------|-----|------|------|
| id | uuid | PK | UUID v7 |
| name | varchar(100) | NOT NULL, UNIQUE | 部署名 |
| version | bigint | NOT NULL, DEFAULT 0 | 楽観ロック |
| created_at | timestamptz | NOT NULL | 作成日時 |
| updated_at | timestamptz | NOT NULL | 更新日時 |

```sql
CREATE TABLE departments (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

---

### employees（社員）

| カラム | 型 | 制約 | 説明 |
|--------|-----|------|------|
| id | uuid | PK | UUID v7 |
| name | varchar(100) | NOT NULL | 氏名 |
| email | varchar(255) | NOT NULL, UNIQUE | メールアドレス（ログインID） |
| password | varchar(255) | NOT NULL | BCrypt ハッシュ |
| department_id | uuid | FK → departments(id), NOT NULL | 所属部署 |
| role | varchar(20) | NOT NULL, CHECK | EMPLOYEE / ADMIN |
| is_manager | boolean | NOT NULL, DEFAULT false | 部署の上長フラグ |
| hire_date | date | NOT NULL | 入社日 |
| retire_date | date | | 退職日（null = 在籍中） |
| version | bigint | NOT NULL, DEFAULT 0 | 楽観ロック |
| created_at | timestamptz | NOT NULL | 作成日時 |
| updated_at | timestamptz | NOT NULL | 更新日時 |

```sql
CREATE TABLE employees (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    department_id UUID NOT NULL REFERENCES departments(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('EMPLOYEE', 'ADMIN')),
    is_manager BOOLEAN NOT NULL DEFAULT false,
    hire_date DATE NOT NULL,
    retire_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_employees_department_id ON employees(department_id);
CREATE INDEX idx_employees_role ON employees(role);
```

制約:
- 1 部署につき `is_manager = true` は 1 人のみ → アプリ層で制御（部分一意インデックスでも可能だが、エラーメッセージの制御が難しい）

---

### attendance_records（打刻記録）

| カラム | 型 | 制約 | 説明 |
|--------|-----|------|------|
| id | uuid | PK | UUID v7 |
| employee_id | uuid | FK → employees(id), NOT NULL | 打刻した社員 |
| work_date | date | NOT NULL | 勤務日 |
| clock_in | timestamptz | NOT NULL | 出勤時刻 |
| clock_out | timestamptz | | 退勤時刻（null = 出勤中） |
| corrected | boolean | NOT NULL, DEFAULT false | 修正済みフラグ |
| version | bigint | NOT NULL, DEFAULT 0 | 楽観ロック |
| created_at | timestamptz | NOT NULL | 作成日時 |
| updated_at | timestamptz | NOT NULL | 更新日時 |

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

備考:
- 同一 `employee_id` + `work_date` に複数行が存在可能（同日複数回打刻）
- ユニーク制約は付けない

---

### attendance_corrections（勤怠修正申請）

| カラム | 型 | 制約 | 説明 |
|--------|-----|------|------|
| id | uuid | PK | UUID v7 |
| attendance_record_id | uuid | FK → attendance_records(id) | 修正対象の打刻記録。打刻忘れの場合は null |
| requester_id | uuid | FK → employees(id), NOT NULL | 申請者 |
| approver_id | uuid | FK → employees(id) | 承認/却下した上長（処理前は null） |
| target_date | date | NOT NULL | 修正対象日 |
| corrected_clock_in | timestamptz | NOT NULL | 修正後の出勤時刻 |
| corrected_clock_out | timestamptz | NOT NULL | 修正後の退勤時刻 |
| reason | varchar(500) | NOT NULL | 修正理由 |
| status | varchar(20) | NOT NULL, CHECK | PENDING / APPROVED / REJECTED |
| version | bigint | NOT NULL, DEFAULT 0 | 楽観ロック |
| created_at | timestamptz | NOT NULL | 作成日時 |
| updated_at | timestamptz | NOT NULL | 更新日時 |

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

---

## ER 図

```
┌─────────────────┐         ┌─────────────────────────┐
│   departments   │         │       employees         │
├─────────────────┤         ├─────────────────────────┤
│ id (PK)         │1      N │ id (PK)                 │
│ name            │─────────│ department_id (FK)       │
│ version         │         │ name                    │
│ created_at      │         │ email (UQ)              │
│ updated_at      │         │ password                │
└─────────────────┘         │ role                    │
                            │ is_manager              │
                            │ hire_date               │
                            │ retire_date             │
                            │ version                 │
                            │ created_at              │
                            │ updated_at              │
                            └──────┬───────┬──────────┘
                                   │1      │1
                                   │       │
                                N  │       │  N
                    ┌──────────────┘       └───────────────────┐
                    │                                          │
     ┌──────────────▼──────────────┐   ┌───────────────────────▼──────────────┐
     │    attendance_records       │   │     attendance_corrections           │
     ├─────────────────────────────┤   ├──────────────────────────────────────┤
     │ id (PK)                     │   │ id (PK)                              │
     │ employee_id (FK)            │   │ attendance_record_id (FK, nullable)  │
     │ work_date                   │   │ requester_id (FK → employees)        │
     │ clock_in                    │   │ approver_id (FK → employees)         │
     │ clock_out                   │0..1│ target_date                          │
     │ corrected                   │◄──│ corrected_clock_in                   │
     │ version                     │ N │ corrected_clock_out                  │
     │ created_at                  │   │ reason                               │
     │ updated_at                  │   │ status                               │
     └─────────────────────────────┘   │ version                              │
                                       │ created_at                           │
                                       │ updated_at                           │
                                       └──────────────────────────────────────┘
```

リレーション:
- `departments` 1 : N `employees` — 1部署に複数社員
- `employees` 1 : N `attendance_records` — 1社員に複数打刻
- `employees` 1 : N `attendance_corrections` (requester) — 1社員が複数の修正申請
- `employees` 1 : N `attendance_corrections` (approver) — 1上長が複数の申請を承認
- `attendance_records` 0..1 : N `attendance_corrections` — 1打刻に対して複数申請（却下→再申請）。打刻忘れは null

---

## インデックス設計

| テーブル | インデックス | 用途 |
|---------|------------|------|
| employees | `idx_employees_department_id` | 部署別社員一覧 |
| employees | `idx_employees_role` | ロール別フィルタ |
| attendance_records | `idx_attendance_records_employee_date` | 社員×日付での検索（履歴表示・打刻チェック） |
| attendance_corrections | `idx_attendance_corrections_requester` | 自分の申請一覧 |
| attendance_corrections | `idx_attendance_corrections_status` | ステータス別フィルタ（承認待ち一覧） |

---

## Flyway マイグレーション計画

### DDL（全環境共通）

配置先: `src/main/resources/db/migration/`

| ファイル | 内容 |
|---------|------|
| `V1__create_departments.sql` | departments テーブル |
| `V2__create_employees.sql` | employees テーブル + インデックス |
| `V3__create_attendance_records.sql` | attendance_records テーブル + インデックス |
| `V4__create_attendance_corrections.sql` | attendance_corrections テーブル + インデックス |

### 初期データ（dev / test のみ）

配置先: `src/main/resources/db/seed/`

| ファイル | 内容 |
|---------|------|
| `V1000__seed_data.sql` | デモ用初期データ（部署・社員・勤怠サンプル） |

### Flyway ロケーション設定

```yaml
# application.yml（共通）
spring.flyway.locations: classpath:db/migration

# application-dev.yml / application-test.yml
spring.flyway.locations: classpath:db/migration,classpath:db/seed
```

本番には DDL のみ適用され、デモ用データは入らない。
