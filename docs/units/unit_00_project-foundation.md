# Unit 00: プロジェクト基盤整備

全ドメインが依存する共通基盤。他の全 Unit の前提条件。

## 依存関係

- 依存先: なし（最初に実装）
- 依存元: 全 Unit

## スコープ

### Backend (Spring Boot)

| 対象 | 内容 |
|------|------|
| プロジェクト初期化 | Spring Boot 3.x, Java 21, Gradle |
| 依存ライブラリ | Spring Web, Spring Data JPA, Spring Security (骨格のみ), Flyway, Testcontainers, JaCoCo, Checkstyle, SpotBugs, uuid-creator, Logstash Logback Encoder, Micrometer Tracing, Actuator, SpringDoc (OpenAPI) |
| Docker Compose | PostgreSQL 17 |
| Flyway | 初期設定（ロケーション構成: `db/migration` + `db/seed`） |
| Spring Security | `SecurityFilterChain` の骨格（この Unit では全エンドポイント `permitAll`） |
| 例外ハンドリング | `@RestControllerAdvice` + RFC 9457 Problem Details |
| 構造化ログ | `logback-spring.xml`（dev: plaintext, prod: JSON） |
| リクエストトレーシング | Micrometer Tracing（`traceId` 自動付与） |
| Actuator | health, info, metrics エンドポイント |
| テスト基盤 | Testcontainers 設定、`application-test.yml` |
| 静的解析 | Checkstyle (Google Java Style), SpotBugs |
| ArchUnit | テスト基盤のみ（レイヤールール・ドメイン依存ルールの骨格） |
| 監査カラム | Spring Data Auditing 設定（`createdAt`, `updatedAt` 自動設定） |
| パッケージ構成 | `com.example.attendance.common.{config, exception, logging, util}` |

### Frontend (Next.js)

| 対象 | 内容 |
|------|------|
| プロジェクト初期化 | Next.js (App Router), TypeScript |
| UI ライブラリ | shadcn/ui + Tailwind CSS |
| 静的解析 | Biome |
| サーバーステート | TanStack Query 設定 |
| API クライアント | fetch ラッパー（ベース URL、エラーハンドリング、CSRF トークン） |
| 型定義 | `types/index.ts`（共通型） |
| 共通コンポーネント | `AppLayout`, `Sidebar`(骨格), `Header`(骨格), `DataTable`, `FormDialog`, `ConfirmDialog`, `Toast`, `MonthSelector`, `StatusBadge` |
| ルートレイアウト | `app/layout.tsx`, `app/(authenticated)/layout.tsx` の骨格 |

### DevOps

| 対象 | 内容 |
|------|------|
| Docker Compose | `docker-compose.yml`（PostgreSQL） |
| ルート package.json | `db:up`, `db:down`, `boot`, `dev` 等のコマンド |

## 作成するファイル（Backend）

```
packages/backend/
├── build.gradle
├── settings.gradle
├── config/
│   ├── checkstyle/checkstyle.xml
│   └── spotbugs/spotbugs-exclude.xml
└── src/
    ├── main/
    │   ├── java/com/example/attendance/
    │   │   ├── AttendanceApplication.java
    │   │   └── common/
    │   │       ├── config/
    │   │       │   ├── SecurityConfig.java
    │   │       │   ├── CorsConfig.java
    │   │       │   ├── JpaAuditingConfig.java
    │   │       │   └── ActuatorConfig.java
    │   │       └── exception/
    │   │           ├── GlobalExceptionHandler.java
    │   │           └── ProblemDetailFactory.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-test.yml
    │       ├── application-prod.yml
    │       ├── logback-spring.xml
    │       └── db/
    │           ├── migration/    (空、Unit 01 から使用)
    │           └── seed/         (空、後で追加)
    └── test/
        ├── java/com/example/attendance/
        │   ├── AttendanceApplicationTests.java
        │   └── architecture/
        │       ├── LayerDependencyTest.java
        │       └── DomainDependencyTest.java
        └── resources/
            └── application-test.yml
```

## 作成するファイル（Frontend）

```
packages/frontend/
├── package.json
├── tsconfig.json
├── next.config.ts
├── tailwind.config.ts
├── biome.json
└── src/
    ├── app/
    │   ├── layout.tsx
    │   ├── login/page.tsx          (プレースホルダー)
    │   └── (authenticated)/
    │       └── layout.tsx          (プレースホルダー)
    ├── components/
    │   ├── ui/                     (shadcn/ui)
    │   ├── layout/
    │   │   ├── AppLayout.tsx
    │   │   ├── Sidebar.tsx
    │   │   └── Header.tsx
    │   ├── DataTable.tsx
    │   ├── FormDialog.tsx
    │   ├── ConfirmDialog.tsx
    │   ├── Toast.tsx
    │   ├── MonthSelector.tsx
    │   └── StatusBadge.tsx
    ├── lib/
    │   ├── api-client.ts
    │   ├── query-client.ts
    │   └── validators.ts
    └── types/
        └── index.ts
```

## テストケース

| テスト | 種類 | 内容 |
|--------|------|------|
| `AttendanceApplicationTests` | 統合 | Spring Boot コンテキスト起動確認 |
| `LayerDependencyTest` | ArchUnit | Controller→Service→Repository の方向のみ許可 |
| `DomainDependencyTest` | ArchUnit | 許可されたドメイン間依存のみ |
| Actuator health | 統合 | `/actuator/health` が 200 を返す |

## 完了条件

- [ ] `./gradlew build` が成功する
- [ ] Testcontainers で PostgreSQL が起動し、Flyway マイグレーション（空）が実行される
- [ ] Actuator `/actuator/health` が `UP` を返す
- [ ] ArchUnit テストが通る
- [ ] `npm run dev` でフロントエンド開発サーバーが起動する
- [ ] `npm run lint` が通る
- [ ] 共通コンポーネントが表示される（Storybook は不要、ページ上で確認）
