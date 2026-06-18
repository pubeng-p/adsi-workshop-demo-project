# Unit of Work 分割

基本設計をもとに、独立して実装可能な Unit of Work に分解する。

## 実装戦略: インターフェース先行 → 並列実装

Unit 間の依存はインターフェース（Entity 形状・Service interface・API 契約）に集中している。
これらを先に定義することで、Unit 01〜04 の実装本体を並列化する。

## 依存関係図

```
Unit 00: プロジェクト基盤整備
    │
    ▼
Phase A: インターフェース定義（Unit 01〜04 分まとめて）
  - Flyway マイグレーション V1〜V3
  - Entity クラス（フィールド定義）
  - Service interface
  - DTO record 定義
  - Enum（Role, AttendanceStatus, CorrectionStatus）
    │
    ▼
Phase B: 実装（Unit 01〜04 並列）
  各 Unit の Service 実装・Controller・テスト・Frontend
    │
    ├─────────┬─────────┐
    ▼         ▼         ▼
  Unit 01   Unit 02   Unit 03 / 04
  部署管理  社員管理   認証 / 打刻
    │
    ▼
Phase C: 実装（Unit 05 / 06 並列）
  - Flyway V4（corrections テーブル）
    │
    ├──────────┐
    ▼          ▼
  Unit 05    Unit 06
  勤怠修正   月次集計
```

### Phase A で定義するもの

| カテゴリ | 対象 | 理由 |
| -------- | ---- | ---- |
| Flyway | V1〜V3（departments, employees, attendance_records） | FK 依存のため実行順は固定。先に全部書く |
| Entity | Department, Employee, AttendanceRecord | 他 Unit から FK / 参照される |
| Enum | Role, AttendanceStatus | 複数 Unit で共有 |
| Service interface | 各ドメインの interface | 並列実装時に他 Unit をモックするため |
| DTO record | Request/Response の record 定義 | API 契約の確定 |

### Phase B の並列化が可能な理由

- **Unit テスト**: Service interface をモックするため、実装の依存がない
- **WebMvcTest**: SecurityFilterChain はモック可能。認証は `@WithMockUser` 等で回避
- **DataJpaTest**: Flyway が Phase A で全テーブルを作るため、Repository テストは独立に動く
- **Frontend**: API 契約（DTO）が決まっていれば、MSW でモックして並列開発可能

### 注意点

- **統合テスト**は Phase B 完了後にまとめて実施（全 Unit が揃った状態で）
- **SecurityFilterChain の権限マトリクス**は Unit 03 で実装するが、Phase A で API パスと権限の対応表を確定させておく

## Unit 一覧

| Unit | 名称 | Phase | 主なテーブル | 主な API |
| ---- | ---- | ----- | ------------ | ------- |
| 00 | [プロジェクト基盤整備](unit_00_project-foundation.md) | 前提 | — | Actuator |
| 01 | [部署管理](unit_01_department.md) | B | departments | GET/POST/PUT departments |
| 02 | [社員管理](unit_02_employee.md) | B | employees | GET/POST/PUT/PATCH employees |
| 03 | [認証](unit_03_auth.md) | B | — | login, logout, me |
| 04 | [打刻・勤怠](unit_04_attendance.md) | B | attendance_records | clock-in/out, history, team, all |
| 05 | [勤怠修正](unit_05_correction.md) | C | attendance_corrections | corrections, pending, approve, reject |
| 06 | [月次集計・帳票](unit_06_report.md) | C | — | monthly, csv, pdf |

## 実装順序

1. **Unit 00** → 全 Unit の前提（基盤整備 + 起動確認）
2. **Phase A** → インターフェース定義（Flyway V1〜V3 + Entity + Service interface + DTO）
3. **Phase B: Unit 01〜04** → **並列実装**（Service 実装・Controller・テスト・Frontend）
4. **Phase C: Unit 05 / 06** → **並列実装**（Flyway V4 + 修正・集計の実装）

## Flyway マイグレーション順序

| バージョン | ファイル | 作成タイミング |
| ---------- | ------- | -------------- |
| V1 | `V1__create_departments.sql` | Phase A |
| V2 | `V2__create_employees.sql` | Phase A |
| V3 | `V3__create_attendance_records.sql` | Phase A |
| V4 | `V4__create_attendance_corrections.sql` | Phase C |
| V1000 | `V1000__seed_data.sql` | Phase B 完了後 |
