# Unit 02: 社員管理

社員マスタの CRUD。認証・打刻・修正の全ドメインが参照する中心的な Entity。

## 依存関係

- 依存先: Unit 00（共通基盤）, Unit 01（部署 — FK 参照）
- 依存元: Unit 03（認証）, Unit 04（打刻）, Unit 05（修正）, Unit 06（集計）

## ユーザーストーリー

- **EMP-01**: 管理者として、社員を新規登録したい
- **EMP-02**: 管理者として、社員情報を編集したい
- **EMP-03**: 管理者として、社員を退職処理したい
- **EMP-04**: 管理者として、社員一覧を確認したい（フィルタ・ページネーション）
- **EMP-05**: 管理者として、部署の上長を設定したい
- **EMP-06**: 管理者として、退職社員の情報も参照したい

## テーブル

### employees

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

Flyway: `V2__create_employees.sql`

## API

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/employees` | 社員一覧 | 管理者 |
| GET | `/api/employees/{id}` | 社員詳細 | 管理者 |
| POST | `/api/employees` | 社員登録 | 管理者 |
| PUT | `/api/employees/{id}` | 社員編集 | 管理者 |
| PATCH | `/api/employees/{id}/retire` | 退職処理 | 管理者 |
| PATCH | `/api/employees/{id}/manager` | 上長設定 | 管理者 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/admin/employees` | 社員管理 | `EmployeeTable`, `EmployeeFormDialog`, `EmployeeFilters`, `RetireDialog`, `ManagerToggle` |

## Backend 実装順序（TDD）

1. Flyway マイグレーション `V2__create_employees.sql`
2. `Role` Enum
3. `Employee` Entity
4. `EmployeeRepository` テスト → 実装
5. `EmployeeService` テスト → interface → 実装
6. `EmployeeController` テスト → 実装
7. 統合テスト

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/employee/
│   ├── controller/EmployeeController.java
│   ├── dto/
│   │   ├── EmployeeCreateRequest.java    (record)
│   │   ├── EmployeeUpdateRequest.java    (record)
│   │   ├── RetireRequest.java            (record)
│   │   ├── ManagerRequest.java           (record)
│   │   └── EmployeeResponse.java         (record)
│   ├── entity/
│   │   ├── Employee.java
│   │   └── Role.java                     (enum)
│   ├── repository/EmployeeRepository.java
│   └── service/
│       ├── EmployeeService.java          (interface)
│       └── EmployeeServiceImpl.java
├── main/resources/db/migration/
│   └── V2__create_employees.sql
└── test/java/com/example/attendance/employee/
    ├── controller/EmployeeControllerTest.java
    ├── repository/EmployeeRepositoryTest.java
    └── service/EmployeeServiceTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/employee/
├── EmployeeTable.tsx
├── EmployeeFormDialog.tsx
├── EmployeeFilters.tsx
├── RetireDialog.tsx
├── ManagerToggle.tsx
├── useEmployees.ts
└── employee-api.ts

packages/frontend/src/app/(authenticated)/admin/employees/
└── page.tsx
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| Repository: 社員登録 | DataJpaTest | 社員を保存して取得できる |
| Repository: メール重複 | DataJpaTest | 同一メールで例外 |
| Repository: 部署別検索 | DataJpaTest | departmentId で絞り込み |
| Service: 社員一覧（フィルタ・ページ） | Unit | フィルタ条件に応じた結果 |
| Service: 社員登録 | Unit | パスワードが BCrypt ハッシュ化される |
| Service: 社員登録（メール重複） | Unit | 409 例外 |
| Service: 社員編集 | Unit | 名前・メール・部署・ロールが更新 |
| Service: 退職処理 | Unit | retireDate が設定される |
| Service: 上長設定 | Unit | isManager が true になる |
| Service: 上長設定（同部署に既に上長） | Unit | 409 例外 |
| Controller: GET /api/employees | WebMvcTest | 200 + ページネーション |
| Controller: POST /api/employees | WebMvcTest | 201 + 社員情報（password 含まない） |
| Controller: PUT /api/employees/{id} | WebMvcTest | 200 + 更新後情報 |
| Controller: PATCH retire | WebMvcTest | 200 + retireDate 設定済み |
| Controller: PATCH manager | WebMvcTest | 200 + isManager 更新済み |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| EmployeeTable: 一覧表示 | Component | API データをテーブルに表示 |
| EmployeeTable: ページネーション | Component | ページ切り替えで API 再取得 |
| EmployeeFilters: フィルタ適用 | Component | 部署・ロール選択で絞り込み |
| EmployeeFormDialog: 新規登録 | Component | フォーム入力 → 送信 |
| EmployeeFormDialog: バリデーション | Component | 必須項目・メール形式チェック |
| RetireDialog: 退職処理 | Component | 退職日入力 → 確認 → 送信 |
| ManagerToggle: 上長設定 | Component | トグル切り替え → API 呼び出し |

## ビジネスルール

- パスワードは BCrypt ハッシュで保存
- メールアドレスはユニーク
- 1 部署につき `isManager = true` は 1 人のみ（アプリ層で制御）
- `retireDate` が設定されている社員はログイン不可（Unit 03 で実装）
- `role = ADMIN` と `isManager = true` は兼任可能

## 完了条件

- [ ] Flyway マイグレーションが正常に実行される（departments テーブルの FK 参照を含む）
- [ ] 社員 CRUD の全 API が動作する
- [ ] パスワードが BCrypt ハッシュで保存されている
- [ ] ページネーション・フィルタが正しく機能する
- [ ] 上長設定で同部署の重複チェックが動作する
- [ ] フロントエンド社員管理画面で一覧・登録・編集・退職・上長設定ができる
- [ ] Backend テストカバレッジ 80% 以上（employee パッケージ）
