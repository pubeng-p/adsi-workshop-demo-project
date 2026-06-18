# 01: モノレポ化 + フロントエンド + インフラ整備

## プロンプト

### フロントエンド追加

> あと、このアプリの画面も作りたい。どういうプロジェクト構成にするべき？

→ 選択: **Next.js (React) + モノレポ構成**

### フロント→バックエンド連携

> rewrites でプロキシもしたいし、あと、バックエンドで ./gradlew dev みたいにしたら docker compose up と bootRun を同時実行とかできない？

→ Next.js の rewrites プロキシ + Spring Boot Docker Compose サポートで対応

### ドメインディレクトリの削除

> ちなみに、勤怠管理アプリということだけ決まっているが、ドメイン分けとかの部分は後から要件定義→設計で詰めていきたいのでそういう部分のファイルは消せるなら消してほしい

→ 事前に作っていた domain/ 以下の空ディレクトリを全削除

### インフラ整備

> infra デプロイができるような環境も整えておキタイ。どういう構成にするかは私に聞いて。

→ 選択: **AWS CDK (TypeScript) + infra/ ディレクトリ + dev/prod 2環境**

---

## やったこと

### 1. モノレポ化

Spring Boot のファイルを `backend/` に移動し、ルートレベルを整理した。

- `build.gradle.kts`, `src/`, `compose.yaml`, `gradlew` 等 → `backend/` に移動
- ルートの `.gitignore` をモノレポ用に書き換え
- `.mise.toml` に `node = "22"` を追加
- `CLAUDE.md` を更新（プロジェクト全体のルールに）

### 2. Next.js プロジェクト生成

```bash
npx create-next-app@latest frontend \
  --typescript --tailwind --eslint --app --src-dir --use-npm
```

- create-next-app が生成した `.git`, `CLAUDE.md`, `AGENTS.md` を削除（モノレポなのでルートで管理）

### 3. フロント→バックエンド連携

#### Next.js rewrites プロキシ

`frontend/next.config.ts` に rewrites を設定:

```typescript
async rewrites() {
  return [
    {
      source: "/api/:path*",
      destination: "http://localhost:8080/api/:path*",
    },
  ];
},
```

→ フロント側は `/api/...` に fetch するだけで、CORS を気にしなくて良い。

#### Spring Boot Docker Compose 自動起動

`spring-boot-docker-compose` 依存を追加:

```kotlin
implementation("org.springframework.boot:spring-boot-docker-compose")
```

- `./gradlew bootRun` するだけで PostgreSQL も自動起動
- `compose.yaml` から接続情報を自動検出するので、`application-dev.yaml` から datasource 設定を削除
- `lifecycle-management: start-and-stop` でアプリ停止時に Docker も停止

### 4. ドメインディレクトリの削除

要件定義前にディレクトリ構造を決めるのは時期尚早だったため、以下を削除:

- `domain/attendance/` (controller, service, repository, entity, dto)
- `domain/employee/` (同上)
- `common/exception/`
- `config/`
- テスト側の `architecture/`, `domain/`

### 5. AWS CDK プロジェクト生成

```bash
cd infra && npx cdk init app --language typescript
```

- エントリポイント (`bin/infra.ts`): `--context env=dev|prod` で環境を切り替え
- スタック (`lib/attendance-stack.ts`): リソースは要件定義後に追加。タグ付けだけ設定
- `cdk synth --context env=dev` で CloudFormation テンプレート生成を確認済み

デプロイ方法:

```bash
# dev 環境
npx cdk deploy --context env=dev

# prod 環境
npx cdk deploy --context env=prod
```

---

## 開発の起動方法

```bash
# ターミナル 1: backend (PostgreSQL も自動起動)
cd backend && ./gradlew bootRun

# ターミナル 2: frontend
cd frontend && npm run dev
```

→ ブラウザで `localhost:3000` を開けば、`/api/*` は自動的にバックエンドに転送される。

---

### 6. packages/ ディレクトリへの移動

> packages/ 内に frontend, backend, infra を入れたいかも？

→ `backend/`, `frontend/`, `infra/` を `packages/` 以下に移動。

### 7. ルートに package.json を追加

> プロジェクトルートで、deploy, boot, frontend dev とかを実行するコマンドを用意したい

→ ルートに `package.json` を作成し、npm scripts で各パッケージのコマンドをまとめた。

Makefile や Gradle マルチプロジェクト等の候補もあったが、3パッケージ中2つ (frontend, infra) が npm なので npm scripts が自然という判断。

主なコマンド:

| コマンド | 内容 |
|---------|------|
| `npm run boot` | Spring Boot 起動 (PostgreSQL 自動起動) |
| `npm run dev` | Next.js 開発サーバー |
| `npm run check:backend` | Checkstyle + SpotBugs + テスト + Jacoco |
| `npm run deploy:dev` | CDK で dev 環境デプロイ |
| `npm run deploy:prod` | CDK で prod 環境デプロイ |

---

## 現在のプロジェクト構成

```
java-practice/
├── .mise.toml                ← Java 21 + Node 22
├── .gitignore
├── CLAUDE.md
├── package.json              ← ルートコマンド (npm run boot, dev, deploy:* 等)
├── docs/path/
│   ├── 00-environment-setup.md
│   └── 01-monorepo-and-infra-setup.md
└── packages/
    ├── backend/              ← Spring Boot 3.5.0
    │   ├── build.gradle.kts
    │   ├── compose.yaml
    │   ├── config/checkstyle/
    │   ├── gradlew
    │   └── src/
    ├── frontend/             ← Next.js (TypeScript + Tailwind)
    │   ├── package.json
    │   ├── next.config.ts    ← rewrites プロキシ設定済み
    │   └── src/app/
    └── infra/                ← AWS CDK (TypeScript)
        ├── bin/infra.ts      ← エントリポイント（env=dev|prod）
        ├── lib/attendance-stack.ts
        ├── cdk.json
        └── package.json
```
