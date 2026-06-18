# Unit 01: 部署管理

部署マスタの CRUD。他ドメインから参照される基盤的なマスタデータ。

## 依存関係

- 依存先: Unit 00（共通基盤）
- 依存元: Unit 02（社員管理 — 部署選択）

## ユーザーストーリー

- **DEPT-01**: 管理者として、部署を登録・編集したい
- **DEPT-02**: 管理者として、部署一覧を確認したい

## テーブル

### departments

```sql
CREATE TABLE departments (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Flyway: `V1__create_departments.sql`

## API

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/departments` | 部署一覧 | 全ロール |
| POST | `/api/departments` | 部署登録 | 管理者 |
| PUT | `/api/departments/{id}` | 部署編集 | 管理者 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/admin/departments` | 部署管理 | `DepartmentTable`, `DepartmentFormDialog` |

## Backend 実装順序（TDD）

1. Flyway マイグレーション `V1__create_departments.sql`
2. `Department` Entity
3. `DepartmentRepository` テスト → 実装
4. `DepartmentService` テスト → interface → 実装
5. `DepartmentController` テスト → 実装
6. 統合テスト

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/department/
│   ├── controller/DepartmentController.java
│   ├── dto/
│   │   ├── DepartmentRequest.java        (record)
│   │   └── DepartmentResponse.java       (record)
│   ├── entity/Department.java
│   ├── repository/DepartmentRepository.java
│   └── service/
│       ├── DepartmentService.java        (interface)
│       └── DepartmentServiceImpl.java
├── main/resources/db/migration/
│   └── V1__create_departments.sql
└── test/java/com/example/attendance/department/
    ├── controller/DepartmentControllerTest.java
    ├── repository/DepartmentRepositoryTest.java
    └── service/DepartmentServiceTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/department/
├── DepartmentTable.tsx
├── DepartmentFormDialog.tsx
├── useDepartments.ts
└── department-api.ts

packages/frontend/src/app/(authenticated)/admin/departments/
└── page.tsx
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| Repository: 部署登録 | DataJpaTest | 部署を保存して取得できる |
| Repository: 名前ユニーク制約 | DataJpaTest | 同名部署の保存で例外 |
| Service: 部署一覧取得 | Unit | 全部署がリストで返る |
| Service: 部署登録 | Unit | 正常に登録できる |
| Service: 部署登録（名前重複） | Unit | 409 Conflict 相当の例外 |
| Service: 部署編集 | Unit | 名前が更新される |
| Service: 部署編集（存在しない） | Unit | 404 Not Found 相当の例外 |
| Controller: GET /api/departments | WebMvcTest | 200 + JSON 配列 |
| Controller: POST /api/departments | WebMvcTest | 201 + 登録された部署 |
| Controller: POST（バリデーションエラー） | WebMvcTest | 400 + Problem Details |
| Controller: PUT /api/departments/{id} | WebMvcTest | 200 + 更新された部署 |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| DepartmentTable: 部署一覧表示 | Component | API データをテーブルに表示 |
| DepartmentFormDialog: 新規登録 | Component | フォーム入力 → 送信 |
| DepartmentFormDialog: バリデーション | Component | 空の部署名でエラー表示 |

## ビジネスルール

- フラット構成（親子関係なし）
- 部署名はユニーク
- 所属社員がいる部署は削除不可（※ 削除 API はスコープ外だが、将来対応時のルール）

## 完了条件

- [ ] Flyway マイグレーションが正常に実行される
- [ ] 部署 CRUD の全 API が動作する
- [ ] バリデーションエラーが RFC 9457 形式で返る
- [ ] 楽観ロック (`@Version`) が機能する
- [ ] フロントエンド部署管理画面で一覧・登録・編集ができる
- [ ] Backend テストカバレッジ 80% 以上（department パッケージ）
