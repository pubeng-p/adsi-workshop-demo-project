# 05: Unit of Work 分割

## プロンプト

> Unit of Work 分割に進もう。

> 01〜04 あたりって、インターフェースだけそれぞれちゃんと決めておけば並列実装できないか？

## やったこと

基本設計（`docs/design/`）をもとに、独立して実装可能な Unit of Work に分割した。
さらに、Unit 間の依存がインターフェース（Entity 形状・Service interface・API 契約）に集中していることに着目し、インターフェースを先行定義して Unit 01〜04 を並列実装する戦略を採用した。

### Unit 一覧

| Unit | 名称 | 主なテーブル | 主な API |
| ---- | ---- | ------------ | -------- |
| 00 | プロジェクト基盤整備 | — | Actuator |
| 01 | 部署管理 | departments | GET/POST/PUT departments |
| 02 | 社員管理 | employees | GET/POST/PUT/PATCH employees |
| 03 | 認証 | — | login, logout, me |
| 04 | 打刻・勤怠 | attendance_records | clock-in/out, history, team, all |
| 05 | 勤怠修正 | attendance_corrections | corrections, pending, approve, reject |
| 06 | 月次集計・帳票 | — | monthly, csv, pdf |

### 実装戦略の変更

当初は Unit 00 → 01 → 02 → 03 → 04 → 05/06 の直列実装を想定していたが、以下の理由で Phase 方式に変更した。

**変更前（直列）:**

```
00 → 01 → 02 → 03 → 04 → 05/06（並列）
```

**変更後（Phase 方式）:**

```
Unit 00 → Phase A（インターフェース定義）→ Phase B（01〜04 並列）→ Phase C（05/06 並列）
```

### Phase 構成

| Phase | 内容 | 備考 |
| ----- | ---- | ---- |
| Unit 00 | プロジェクト基盤整備 | 全 Unit の前提。完了後に起動確認 |
| Phase A | インターフェース定義 | Flyway V1〜V3、Entity、Service interface、DTO record、Enum を一括定義 |
| Phase B | Unit 01〜04 並列実装 | Service 実装・Controller・テスト・Frontend |
| Phase C | Unit 05/06 並列実装 | Flyway V4 + 修正・集計の実装 |

### 並列化が可能な根拠

- **Unit テスト**: Service interface をモックするため、実装の依存がない
- **WebMvcTest**: SecurityFilterChain はモック可能。認証は `@WithMockUser` 等で回避
- **DataJpaTest**: Flyway が Phase A で全テーブルを作るため、Repository テストは独立に動く
- **Frontend**: API 契約（DTO）が決まっていれば、MSW でモックして並列開発可能
- **統合テスト**: Phase B 完了後にまとめて実施

## 最終構成

```
docs/units/
├── README.md                         ← 実装戦略（Phase 方式）+ 依存関係図
├── unit_00_project-foundation.md
├── unit_01_department.md
├── unit_02_employee.md
├── unit_03_auth.md
├── unit_04_attendance.md
├── unit_05_correction.md
└── unit_06_report.md
```
