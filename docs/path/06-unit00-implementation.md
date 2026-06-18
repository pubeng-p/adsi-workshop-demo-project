# 06: Unit 00 実装（プロジェクト基盤整備）

## プロンプト

> Unit 00 を進めたいが、まず、現状のディレクトリ構造や設定で足りていない点、乖離している点をまとめて

> これは一気に進めていいよ

> git 管理しよう。一旦現状までを適切に分割してコミットお願い

> フロントエンドからバックエンドにリクエストを送ってフロントエンド側で内容を表示できるところ（疎通確認）までやるのは厳しい？
> あと、CORS については、localhost:3003 とか異なるポートでデプロイされることもあるのでケアして

## やったこと

### Backend

#### 依存ライブラリ追加（build.gradle.kts）

- `spring-boot-starter-actuator` — ヘルスチェック・メトリクス
- `micrometer-tracing-bridge-brave` — リクエストトレーシング（traceId 自動付与）
- `logstash-logback-encoder` — JSON 構造化ログ（prod 環境用）
- `uuid-creator` — UUID v7 生成
- `spring-boot-testcontainers` / `testcontainers:postgresql` — テスト用 PostgreSQL
- `h2` をテストランタイムに移動（Testcontainers フォールバック用）

#### 設定ファイル

| ファイル | 内容 |
|---------|------|
| `application.yaml` | Flyway seed ロケーション追加、Actuator エンドポイント設定 |
| `application-dev.yaml` | CORS 許可オリジン（localhost:3000〜3003） |
| `application-test.yaml` | H2 インメモリ DB、CORS 設定 |
| `application-prod.yaml` | 新規作成。Flyway は migration のみ、CORS は環境変数 |
| `logback-spring.xml` | dev: plaintext, prod: JSON, test: WARN レベル |

#### Java ソース

| ファイル | 内容 |
|---------|------|
| `SecurityConfig` | `SecurityFilterChain` で全エンドポイント `permitAll`（骨格） |
| `CorsConfig` | `app.cors.allowed-origins` プロパティで許可オリジンを外部化 |
| `JpaAuditingConfig` | `@EnableJpaAuditing` |
| `ActuatorConfig` | アプリ情報の `InfoContributor` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` + RFC 9457 Problem Details |
| `ProblemDetailFactory` | ProblemDetail のファクトリメソッド |
| `SystemController` | `GET /api/system/health` 疎通確認用エンドポイント |

#### テスト

| ファイル | 内容 |
|---------|------|
| `AttendanceApplicationTests` | コンテキスト起動 + Actuator health テスト |
| `TestcontainersConfiguration` | Testcontainers 設定（Docker 互換時に利用） |
| `LayerDependencyTest` | ArchUnit レイヤー依存ルール（`optionalLayer` で空パッケージ対応） |
| `DomainDependencyTest` | ArchUnit ドメイン依存ルール（`allowEmptyShould` で空パッケージ対応） |

### Frontend

#### ライブラリ移行・導入

- ESLint → **Biome** に移行（Tailwind CSS ディレクティブ対応設定含む）
- **shadcn/ui** 導入（dialog, table, sonner, badge, select, sidebar 等）
- **TanStack Query** 導入（providers.tsx, query-client.ts）

#### 共通基盤

| ファイル | 内容 |
|---------|------|
| `api-client.ts` | fetch ラッパー（ベース URL、エラーハンドリング、型付き） |
| `query-client.ts` | TanStack Query のクライアント設定 |
| `types/index.ts` | 共通型定義（PaginatedResponse, ApiErrorResponse） |

#### 共通コンポーネント

AppLayout, Sidebar（メニュー骨格）, Header, DataTable, FormDialog, ConfirmDialog, Toast（sonner ラッパー）, MonthSelector, StatusBadge

#### ルーティング

| パス | 内容 |
|-----|------|
| `layout.tsx` | ルートレイアウト（Providers, Toaster 組み込み） |
| `page.tsx` | トップページ（Backend 疎通確認表示） |
| `(authenticated)/layout.tsx` | 認証済みルートのレイアウト骨格 |
| `login/page.tsx` | ログインプレースホルダー |

### DevOps

- ルート `package.json` に `db:up` / `db:down` 追加

## つまずき

### Testcontainers が Docker Desktop に接続できない

Docker Desktop 4.73.0（Docker Engine 29.4.3, API 1.54）と docker-java 3.4.2 の間に API 互換性問題。`docker` CLI は正常動作するが、Java クライアントが `/info` エンドポイントで 400 BadRequest を受け取る。docker-java 3.5.1 にアップグレードしても解消せず。

**対処**: テストプロファイルでは H2 インメモリ DB にフォールバック。Testcontainers の設定は残し、CI 環境（Linux ネイティブ Docker）で利用する想定。

### Biome 2.x の設定フォーマット

Biome 2.5.x では `files.ignore` → `files.includes`（除外はネガティブパターン `!path`）、`rules.recommended` → `rules.preset: "recommended"` に変更。`biome migrate --write` で自動移行できた。

### shadcn/ui の asChild 廃止

shadcn/ui の最新版（base-ui ベース）では `asChild` プロップが廃止され、`render` プロップに変更。`<SidebarMenuButton render={<Link href="..." />}>` の形式で対応。

## 完了条件の達成状況

| 条件 | 状態 |
|------|------|
| `./gradlew build` 成功 | OK |
| Testcontainers + Flyway | H2 代替（Docker Desktop 互換性問題） |
| `/actuator/health` が UP | OK |
| ArchUnit テスト通過 | OK |
| `npm run dev` 起動 | OK |
| `npm run lint` 通過 | OK |
| 共通コンポーネント表示 | OK |
| Frontend → Backend 疎通 | OK |
