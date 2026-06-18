# 02: ルール整備 + 開発プロセス策定

## プロンプト

### ルールファイル作成

> .claude/rules とかに、Java と TS をSIerのシステム開発水準のクオリティで開発していくためのルールをまとめてほしい。 everything-claude-code のルールとか参考にしてもいいかも

### TDD + AI 駆動開発プロセス

> テスト駆動開発ベースで進めていきたい。あと、AI駆動開発なんだけど、仕様書駆動っぽい感じでSIerのワークフローに近い形、だけどAI駆動開発に適したプロセスを使いたいと思っている

### AI-DLC との比較

> AI-DLC という AWS が提唱するやつでもこういうことしてて、基本的にPdMのフローに載せてるんだけど、Unit Of Work とかを決めてから実装着手みたいなことをしてた。ここでもそれでいいかな？

→ Unit of Work ベースを採用。AI-DLC の Plan→承認→Implement の2段階ゲートと [Question]/[Answer] タグも導入。

### ドメインモデリングの深さ

> ドメインモデリングはどれくらい導入するかかな

→ **ライト DDD** を採用。Entity + Value Object + Repository + Service。Aggregate Root や Domain Event はフル DDD として必要になったら導入。

理由:
- SIer の標準はレイヤードのみ（60〜70%）。フル DDD は 5〜10%
- デモ規模にフル DDD は重すぎる
- ライト DDD なら「レイヤードだけ」から一歩上を見せられる

### ドキュメント配置

> ここら辺の要件定義→基本設計→UnitOfWork→各Unitのプランのドキュメントを入れる場所を docs 内に用意しておいて

### ルートコマンド

> プロジェクトルートで、deploy, boot, frontend dev とかを実行するコマンドを用意したい

→ ルートに `package.json` を作成。npm scripts で各パッケージのコマンドを束ねた。

### packages/ への移動

> packages/ 内に frontend, backend, infra を入れたいかも？

---

## やったこと

### 1. `.claude/rules/` にルールファイル作成

everything-claude-code のルールを参考に、SIer 水準に合わせた4ファイルを作成:

| ファイル | 適用対象 | 主な内容 |
|---------|---------|---------|
| `java-spring-boot.md` | `packages/backend/**/*.java` | イミュータビリティ、コンストラクタ DI、メソッド30行制限、JPA 規約 |
| `typescript-frontend.md` | `packages/frontend/**/*.{ts,tsx}` | `any` 禁止、サーバーコンポーネント優先、Tailwind |
| `security.md` | `packages/**/*` | シークレット管理、SQL インジェクション防止、認証・認可 |
| `testing.md` | テストファイル全般 | カバレッジ80%、AAA パターン、ArchUnit |

`globs` でファイルパターン指定 → 対象ファイル編集時に自動適用。

### 2. 開発プロセス (`development-process.md`) 作成

AI-DLC ベースのフロー:

```
1. 要件定義 → docs/requirements/
2. 基本設計（AI と壁打ち）→ docs/design/
3. Unit of Work 分割 → docs/units/
4. 各 Unit を Plan→承認→TDD で実装
```

AI-DLC から採用:
- Unit of Work（Bounded Context ベースのタスク分割）
- Plan → 承認 → Implement の2段階ゲート
- [Question]/[Answer] タグ（AI が仮定してはいけない点を明示）

AI-DLC から不採用:
- フル DDD（デモ規模には重い）
- 7段階プロンプト体系（小規模なので対話ベースで十分）
- 専用ロール分け（1人で回すデモ）

### 3. `packages/` ディレクトリへの再構成

`backend/`, `frontend/`, `infra/` を `packages/` 以下に移動。

### 4. ルート `package.json` 作成

```json
{
  "scripts": {
    "boot": "cd packages/backend && ./gradlew bootRun",
    "dev": "cd packages/frontend && npm run dev",
    "check:backend": "cd packages/backend && ./gradlew check",
    "deploy:dev": "cd packages/infra && npx cdk deploy --context env=dev",
    "deploy:prod": "cd packages/infra && npx cdk deploy --context env=prod"
  }
}
```

npm scripts を選んだ理由: 3パッケージ中2つ (frontend, infra) が npm。Makefile や Gradle マルチプロジェクトも候補だったが、TS 開発者に馴染みやすい npm scripts が自然。

### 5. docs ディレクトリ構成

```
docs/
├── path/            ← デモの過程記録
├── requirements/    ← 要件定義（ユーザーストーリー）
├── design/          ← 基本設計（API, DB, ドメイン分析, 画面）
└── units/           ← Unit of Work（unit_*.md + 実装計画）
```

---

## 現在のプロジェクト構成

```
java-practice/
├── .claude/
│   ├── rules/
│   │   ├── java-spring-boot.md
│   │   ├── typescript-frontend.md
│   │   ├── security.md
│   │   ├── testing.md
│   │   └── development-process.md
│   └── settings.local.json
├── .mise.toml
├── .gitignore
├── CLAUDE.md
├── package.json                  ← ルートコマンド
├── docs/
│   ├── path/                     ← 過程記録
│   ├── requirements/             ← 要件定義
│   ├── design/                   ← 基本設計
│   └── units/                    ← Unit of Work
└── packages/
    ├── backend/                  ← Spring Boot 3.5.0
    ├── frontend/                 ← Next.js
    └── infra/                    ← AWS CDK
```
