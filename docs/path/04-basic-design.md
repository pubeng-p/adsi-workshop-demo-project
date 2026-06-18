# 04: 基本設計

## プロンプト

> 基本設計に進む。ドメイン分析 → DB 設計 → API 設計 → 画面設計の順で、作業用 Q&A ドキュメントを使いながら進めてほしい。

> 楽観ロックは RDB を使う以上不要ではないか？
> → Web アプリではリード〜ライトが別トランザクションになるため `@Version` が必要と判明。そのまま採用。

> DTO は必要か？
> → Entity を API で直接返すと `password` や `version` が漏れる。リクエスト/レスポンスの形も Entity と一致しないため必要。`record` で定義するのでコストは低い。

> 運用保守時に AI エージェントが自律的にログをチェックできる仕組みを入れたい。スコープは構造化ログ + Actuator で。

> エラー時に traceId で周辺ログを追跡する仕組みも欲しい。CloudWatch Logs 側の対応で実現する。設計ドキュメントに追記してほしい。

> 環境整備（ArchUnit、CI/CD、Lint 等）の設計がまだ詰まっていない。基盤整備の設計も Q&A 形式で進めてほしい。

> パッケージ構成について、一般的な三層レイヤードとドメイン分割レイヤードの違いを教えてほしい。
> → モダン Java デモとしてドメイン分割レイヤードを採用。

> CSRF 無効とは何か？対策をしないということか？
> → SPA ではリスクが低いため無効化が一般的だが、セキュリティデモとしての価値を考慮し CSRF トークン方式を採用。

> UUID v7 を Hibernate 任せにすると persist 前に ID が使えないのでは？
> → uuid-creator ライブラリでコンストラクタ生成に変更。

> Flyway の初期データが本番環境にも入ってしまうのでは？
> → `db/migration/`（DDL）と `db/seed/`（初期データ）にディレクトリ分離し、`flyway.locations` で環境ごとに切り替える方式に修正。

> docs/path に過程を記録してほしい。途中のプロンプトも意図が同じになるようフォーマルな文体で残すこと。

## やったこと

開発プロセスに従い、5 つの設計ドキュメントを `docs/design/` に作成。各トピックで `docs/working/design/` に Q&A ドキュメントを作り、ユーザーが `[Answer]` を記入して設計判断を確定した。

### 1. ドメイン分析（`docs/design/01-domain-analysis.md`）

Q&A: `docs/working/design/01-domain-analysis.md`（6 問）

| 設計判断 | 決定 |
|---------|------|
| 打刻レコード粒度 | 1 出退勤ペア = 1 レコード |
| 打刻忘れの修正 | 修正申請で新規レコードも作成可能 |
| 修正申請の構造 | 修正後の値を保持（承認時に反映） |
| パッケージ構成 | ドメイン分割レイヤード（6 ドメイン + common） |
| タイムゾーン | UTC 保存・JST 表示（Instant） |
| 社員 ID | UUID v7（uuid-creator、コンストラクタ生成） |

成果物:
- Entity 4 つ: Employee, Department, AttendanceRecord, AttendanceCorrection
- Value Object 1 つ: WorkDuration
- Enum 2 つ: Role, CorrectionStatus
- ドメイン関連図
- パッケージ構成図
- 運用監視設計（構造化ログ + Micrometer Tracing + Actuator）

### 2. DB 設計（`docs/design/02-db-design.md`）

Q&A: `docs/working/design/02-db-design.md`（5 問）

| 設計判断 | 決定 |
|---------|------|
| テーブル命名規則 | snake_case、複数形 |
| UUID の DB 型 | PostgreSQL ネイティブ `uuid` 型 |
| Enum 保存方法 | `varchar` + CHECK 制約 |
| 監査カラム | Spring Data Auditing（`@CreatedDate` / `@LastModifiedDate`） |
| 初期データ投入 | Flyway（`db/seed/` に分離、dev/test のみ適用） |

成果物:
- テーブル 4 つ: departments, employees, attendance_records, attendance_corrections
- DDL（CREATE TABLE 文）
- ER 図
- インデックス設計（5 つ）
- Flyway マイグレーション計画

#### つまずき

初期データを `V5__seed_data.sql` で DDL と同じディレクトリに置く計画だったが、本番環境にもデモデータが入ってしまう問題をユーザーが指摘。`db/migration/`（DDL、全環境）と `db/seed/`（初期データ、dev/test のみ）にディレクトリを分け、`spring.flyway.locations` で切り替える方式に修正した。

### 3. API 設計（`docs/design/03-api-design.md`）

Q&A: `docs/working/design/03-api-design.md`（5 問）

| 設計判断 | 決定 |
|---------|------|
| API パスプレフィックス | `/api/`（バージョニングなし） |
| ページネーション | オフセットベース（Spring Data `Pageable`） |
| エラーレスポンス | RFC 9457 (Problem Details) |
| OpenAPI 管理方法 | 設計ドキュメント + SpringDoc 自動生成の併用 |
| ログイン API | JSON ボディ（SPA 向け） |

成果物:
- 全 26 エンドポイント（認証 3 / 部署 3 / 社員 6 / 打刻 6 / 修正 5 / 帳票 3）
- 各エンドポイントの Request / Response JSON 例
- エンドポイント × 権限マトリクス
- Actuator エンドポイント + 構造化ログ形式の定義

### 4. 画面設計（`docs/design/04-screen-design.md`）

Q&A: `docs/working/design/04-screen-design.md`（4 問）

| 設計判断 | 決定 |
|---------|------|
| UI ライブラリ | shadcn/ui + Tailwind CSS |
| レイアウト | サイドバー + メインコンテンツ |
| 状態管理 | TanStack Query (React Query) |
| フォームバリデーション | React Hook Form + Zod |

成果物:
- 全 11 画面のページ一覧 + ルーティング
- 各画面の ASCII ワイヤーフレーム
- コンポーネント構成（共通コンポーネント 10 個）
- フロントエンドディレクトリ構成（フィーチャーベース）

### 5. 基盤整備設計（`docs/design/05-infrastructure.md`）

Q&A: `docs/working/design/05-infrastructure.md`（10 問）

| 設計判断 | 決定 |
|---------|------|
| ローカル開発環境 | Docker Compose（PostgreSQL） |
| テスト用 DB | Testcontainers |
| ArchUnit ルール | 4 ルール（レイヤー依存・ドメイン間・Entity 独立性・DI） |
| CI/CD | ビルド + テスト + dev 自動デプロイ + タグで prod デプロイ |
| Backend Lint | Checkstyle + SpotBugs |
| Frontend Lint | Biome |
| カバレッジ | JaCoCo + Vitest（80% 閾値） |
| CSRF | CookieCsrfTokenRepository（トークン方式） |
| CORS | 環境変数で許可オリジン指定 |
| UUID v7 | uuid-creator（コンストラクタ生成） |

## 追加要件

基本設計中に以下の要件が追加された:

- **AI エージェント向け監視**: 運用保守時に AI エージェントが自律的にログチェックできる仕組み
  - 構造化ログ（Logstash Logback Encoder で JSON 出力）
  - Micrometer Tracing（`traceId` でリクエスト単位のログ追跡）
  - Spring Boot Actuator（health, info, metrics）
  - CloudWatch Logs + `aws logs` CLI でのログ検索

## 最終構成

```
docs/
├── design/                          ← 基本設計（確定版）
│   ├── 01-domain-analysis.md
│   ├── 02-db-design.md
│   ├── 03-api-design.md
│   ├── 04-screen-design.md
│   └── 05-infrastructure.md
├── working/
│   ├── requirements/                ← 要件定義 Q&A（前ステップ）
│   │   ├── 01-undecided-items.md
│   │   └── 02-user-story-details.md
│   └── design/                      ← 基本設計 Q&A（今ステップ）
│       ├── 01-domain-analysis.md
│       ├── 02-db-design.md
│       ├── 03-api-design.md
│       ├── 04-screen-design.md
│       └── 05-infrastructure.md
├── requirements/
│   └── 01-user-stories.md
├── path/
│   ├── 00-environment-setup.md
│   ├── 01-monorepo-and-infra-setup.md
│   ├── 02-rules-and-process.md
│   ├── 03-requirements-definition.md
│   └── 04-basic-design.md           ← このファイル
└── project-brief.md
```
