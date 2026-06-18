# 基盤整備の設計判断

ドメイン機能の実装前に整備すべき横断的な基盤。

---

## 1. ローカル開発環境

[Question] ローカルの PostgreSQL はどう用意しますか？

- **A: Docker Compose**: `docker-compose.yml` で PostgreSQL を起動。チーム開発では定番
- **B: ローカルインストール**: 直接 PostgreSQL をインストール。Docker 不要だがバージョン管理が面倒
- **C: Testcontainers のみ**: テスト時に自動起動。開発時は H2 のインメモリ DB

提案: **A（Docker Compose）**。開発用の PostgreSQL + （必要なら）pgAdmin をまとめて起動。`npm run db:up` 等で簡単に使えるようにする。

[Answer]

A

---

## 2. テスト用 DB

[Question] テスト実行時の DB はどうしますか？

- **A: Testcontainers**: テスト実行時に Docker で PostgreSQL を自動起動・破棄。本番と同じ DB エンジンで検証できる
- **B: H2 インメモリ**: 高速だが PostgreSQL 固有の機能（`uuid` 型、`timestamptz` 等）が使えない。方言の差異で本番と違う挙動になるリスク
- **C: Docker Compose のテスト用 DB**: 事前に起動が必要。CI では別途セットアップが必要

提案: **A（Testcontainers）**。PostgreSQL ネイティブの `uuid` 型を使う設計なので、H2 では互換性の問題が出る。

[Answer]

A

---

## 3. ArchUnit のルール

CLAUDE.md に「レイヤー違反を検出するテストを書く」とある。

[Question] 以下の ArchUnit ルールで十分ですか？追加・変更があれば教えてください。

1. **レイヤー依存ルール**: Controller → Service → Repository の方向のみ許可。逆方向は禁止
2. **ドメイン間依存ルール**: 許可されたドメイン間依存のみ（例: `correction` → `attendance`, `employee` は OK、`department` → `correction` は NG）
3. **Entity の独立性**: Entity パッケージは他のレイヤー（Controller, Service）に依存しない
4. **DI ルール**: `@Autowired` のフィールドインジェクション禁止

提案: 上記4つで開始し、必要に応じて追加。

[Answer]

上記4つで開始し、必要に応じて追加。

---

## 4. CI/CD パイプライン

[Question] GitHub Actions のパイプラインに含めるステップはどこまでですか？

- **バックエンドビルド + テスト**: `./gradlew build`（コンパイル + テスト）
- **フロントエンドビルド + テスト**: `npm run build`, `npm run test`
- **Lint**: Backend は Checkstyle or SpotBugs、Frontend は ESLint + Prettier
- **ArchUnit**: Gradle テストに含まれるため自動実行
- **デプロイ**: CDK deploy（dev 環境への自動デプロイ）

[Question] 上記に加えて、以下のオプションはどうしますか？

- **A: ビルド + テストのみ**: デプロイは手動。デモとしてはシンプル
- **B: ビルド + テスト + dev 自動デプロイ**: main ブランチにマージしたら dev 環境に自動デプロイ
- **C: ビルド + テスト + dev/prod 両方**: dev は自動、prod は手動承認後デプロイ

提案: **B**。デモとして CI/CD の流れを見せられる。prod デプロイは手動で十分。

[Answer]

C、タグ付けで prod デプロイまで。

---

## 5. バックエンドの Lint / 静的解析

[Question] バックエンドのコード品質チェックツールはどれを使いますか？

- **A: Checkstyle**: コーディング規約の準拠チェック。Google Java Style や Sun 規約ベース
- **B: SpotBugs**: バグパターンの静的検出。NullPointer や リソースリーク等
- **C: 両方**: Checkstyle でスタイル、SpotBugs でバグ検出
- **D: なし**: ArchUnit + テストカバレッジで十分とする

提案: **A（Checkstyle）**。SpotBugs は有用だが設定が重い。デモでは Checkstyle + ArchUnit で十分。

[Answer]

C がいい。設定ってどういう形になる？

---

## 6. フロントエンドの Lint / フォーマッタ

[Question] フロントエンドの Lint 構成はどうしますか？

- **A: ESLint + Prettier**: 定番の組み合わせ。ESLint でコード品質、Prettier でフォーマット
- **B: Biome**: ESLint + Prettier の代替。1ツールで lint + format。高速だが設定の柔軟性はやや劣る

提案: **A（ESLint + Prettier）**。Next.js の公式サポートがあり、プラグインも充実。

[Answer]

B
---

## 7. テストカバレッジの計測

[Question] カバレッジ計測ツールはどうしますか？

### Backend

- **A: JaCoCo**: Gradle プラグイン。カバレッジレポート生成 + 閾値チェック。定番
- **B: なし**: カバレッジ計測はしない

### Frontend

- **A: Vitest の c8/istanbul**: Vitest 組み込みのカバレッジ。設定が簡単
- **B: なし**

提案: 両方 **A**。80% の閾値を CI でチェック。

[Answer]

両方 A 

---

## 8. Spring Security の認証設定

API 設計で JSON ログインを採用した。

[Question] Spring Security のセッション管理で追加の設定はどうしますか？

- **CSRF**: SPA（Next.js）からの API 呼び出しなので CSRF トークンをどうするか
  - **A: CSRF 無効**: SPA + セッション Cookie（SameSite=Lax）で CSRF リスクは低い。デモではシンプルに無効化
  - **B: CSRF トークン（Cookie パターン）**: `CookieCsrfTokenRepository` で Cookie にトークンを渡し、フロントエンドがヘッダーに付与。より安全

- **セッションタイムアウト**: デフォルト 30 分で問題ないか

提案: **A（CSRF 無効）** + タイムアウト 30 分。デモとしてはシンプルさ優先。本番向けなら B を推奨。

[Answer]

B + タイムアウト 30 分

---

## 9. API の CORS 設定

[Question] CORS の許可オリジンはどうしますか？

- **A: 環境変数で指定**: `ALLOWED_ORIGINS=http://localhost:3000` のように設定。環境ごとに変更可能
- **B: プロファイルで固定**: `application-dev.yml` に `http://localhost:3000`、`application-prod.yml` に本番 URL を記載

提案: **A（環境変数）**。インフラ設定との整合性が取りやすい。

[Answer]

A

---

## 10. UUID v7 ライブラリ

ドメイン分析で UUID v7 を選定した。

[Question] UUID v7 の生成ライブラリはどれを使いますか？

- **A: uuid-creator**: Java 向け UUID ライブラリ。v7 対応。軽量
- **B: java-uuid-generator (JUG)**: FasterXML 製。Jackson との親和性が高い
- **C: Hibernate の UUID 生成**: Hibernate 6.x は UUID v7 をサポート。`@UuidGenerator(style = TIME_BASED)` で JPA レベルで設定可能。外部ライブラリ不要

提案: **C（Hibernate）**。Spring Boot 3.x + Hibernate 6.x なら外部依存なしで UUID v7 が使える。

[Answer]

A