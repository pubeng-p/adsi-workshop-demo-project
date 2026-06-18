# 00: 環境構築

## プロンプト（実際の指示）

### CLAUDE.md の作成指示

> Spring Boot 3.x を使いながら Spring Boot 4 にすぐ移行できるような実装を用いるという rule 書いておきたい

### 環境構築の開始指示

> デモプロジェクトを行うにあたっての整備をお願いしてもいい？採否に迷うものがあったら私にきいて

### 選択肢への回答（AI からの質問に対して）

- **題材**: 勤怠管理
- **Spring Security**: 最初から入れる
- **追加ツール**: SpotBugs, ArchUnit を採用 / Testcontainers, devcontainer は不採用

### 実装の制約指示

> まだ本体の実装はしないでね

→ ディレクトリ構成とビルド設定のみ作成し、ドメインのコードは書かなかった。

---

## 目的

Spring Boot 3.x で勤怠管理のデモアプリを作る。
Spring Boot 4.x への移行が最小限で済む実装を徹底し、デモの最後に「移行簡単でしょ？」で締める構成。

## 技術選定

### 採用したもの

| カテゴリ | ツール | 選定理由 |
|---------|-------|---------|
| フレームワーク | Spring Boot 3.5.0 | SIer 標準。4.x 互換の書き方で実装する |
| Java バージョン | 21 (LTS) | SIer 案件の現時点の標準 |
| ビルドツール | Gradle Kotlin DSL | 型安全で IDE 補完が効く。Spring Initializr のデフォルト |
| DB | PostgreSQL 17 (Docker Compose) | 本番想定 |
| テスト用 DB | H2 (PostgreSQL 互換モード) | テスト時は軽量 DB で済ませる |
| マイグレーション | Flyway | SQL ファイルでスキーマ管理。`ddl-auto` は禁止 |
| 認証・認可 | Spring Security | 最初から入れる方針（後付けは影響範囲が大きい） |
| API ドキュメント | SpringDoc OpenAPI (Swagger UI) | API 仕様書の自動生成 |
| コード品質 | Checkstyle, SpotBugs, Jacoco | SIer の品質ゲート。Checkstyle は Google Style ベース |
| アーキテクチャテスト | ArchUnit | レイヤー違反を自動検出 |
| 開発支援 | Lombok | Entity 用のみ。DTO は record を使う |
| Java バージョン管理 | mise (`.mise.toml`) | Node も Java も統一管理できる |

### 採用しなかったもの

| ツール | 理由 |
|-------|------|
| Testcontainers | テスト時の Docker 起動が重い。H2 で代替 |
| devcontainer | IntelliJ メインの現場を想定。不要 |
| MapStruct | record のコンストラクタで手動マッピングすれば十分 |
| Maven | 新規案件なので Gradle を選択 |

## 手順

### 1. Spring Initializr でプロジェクト生成

```bash
curl -s https://start.spring.io/starter.zip \
  -d type=gradle-project-kotlin \
  -d language=java \
  -d bootVersion=3.5.0 \
  -d groupId=com.example \
  -d artifactId=attendance \
  -d name=attendance \
  -d packageName=com.example.attendance \
  -d javaVersion=21 \
  -d "dependencies=web,data-jpa,security,validation,flyway,postgresql,h2,lombok" \
  -o /tmp/attendance.zip

unzip -o /tmp/attendance.zip -x "*.md"
```

**つまずき**: 最初 `bootVersion=3.4.1` で試したら `Invalid Spring Boot version '3.4.1', Spring Boot compatibility range is >=3.5.0` と返された。Spring Initializr はサポート切れのバージョンを拒否する。`3.5.0` に変更して成功。

### 2. build.gradle.kts の拡張

Spring Initializr が生成したベースに以下を追加:

- `checkstyle` プラグイン
- `com.github.spotbugs` プラグイン (v6.1.2)
- `jacoco` プラグイン
- SpringDoc OpenAPI 依存 (`springdoc-openapi-starter-webmvc-ui:2.8.6`)
- ArchUnit 依存 (`archunit-junit5:1.4.0`)
- Jacoco のカバレッジ最低ライン 80% 設定

### 3. application.properties → application.yaml に変更

Spring Initializr は `.properties` で生成するが、YAML の方が見やすいので変更。

プロファイル分離:
- `application.yaml` — 共通設定。`ddl-auto: validate`, `open-in-view: false`
- `application-dev.yaml` — PostgreSQL 接続、SQL ログ出力、DEBUG ログ
- `application-test.yaml` — H2 (PostgreSQL 互換モード)、ログ抑制

### 4. Docker Compose (compose.yaml)

PostgreSQL 17 をローカル開発用に起動する設定。

```yaml
services:
  postgres:
    image: postgres:17
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: attendance
      POSTGRES_USER: attendance
      POSTGRES_PASSWORD: attendance
```

### 5. Checkstyle 設定

`config/checkstyle/checkstyle.xml` を作成。Google Java Style ベースで SIer 向けにルールを絞った。

**つまずき**: `LineLength` モジュールを `TreeWalker` 内に配置したら Checkstyle 10.x でエラーになった。

```
TreeWalker is not allowed as a parent of LineLength
```

Checkstyle 10.x では `LineLength` は `Checker` 直下に置く仕様に変わっていた。`TreeWalker` の外に移動して解決。

### 6. ディレクトリ構成の作成

ドメイン分割レイヤードアーキテクチャ:

```
src/main/java/com/example/attendance/
├── AttendanceApplication.java
├── config/                          ← Security 等の設定クラス
├── common/exception/                ← 共通例外ハンドリング
└── domain/
    ├── attendance/                   ← 勤怠ドメイン
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   └── dto/
    └── employee/                    ← 社員ドメイン
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        └── dto/
```

テスト側にも `architecture/` ディレクトリを用意（ArchUnit テスト用）。

### 7. Java バージョン問題の解決

**つまずき (最大のハマり)**: `./gradlew` を実行すると謎のエラー `What went wrong: 25` だけが表示された。

原因: システムの JAVA_HOME が **Java 25** (Temurin 25) を指しており、Gradle の Kotlin DSL パーサー (Kotlin コンパイラ) が Java 25 を認識できなかった。

```
java.lang.IllegalArgumentException: 25
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse
```

Gradle toolchain は「アプリのコンパイルに使う JDK」を指定するもので、「Gradle 自体を動かす JVM」は別。Gradle 自体が Java 25 で動いてしまい、Kotlin DSL のパースで死んでいた。

解決手順:

1. mise で Java 21 をインストール
   ```bash
   mise install java@temurin-21
   ```
2. `.mise.toml` でプロジェクトの Java バージョンを固定
   ```toml
   [tools]
   java = "temurin-21"
   ```
3. `gradle.properties` で Gradle が使う JVM を明示指定
   ```properties
   org.gradle.java.home=/Users/nkyos/.local/share/mise/installs/java/temurin-21.0.11+10.0.LTS
   ```
4. `gradle.properties` はローカル環境依存の絶対パスなので `.gitignore` に追加

### 8. テストプロファイルの設定

**つまずき**: `./gradlew check` で `AttendanceApplicationTests` が失敗。`@SpringBootTest` がデフォルトで `dev` プロファイル（PostgreSQL）を使おうとして DB 接続エラー。

テストクラスに `@ActiveProfiles("test")` を追加して H2 を使うように修正。

### 9. ビルド確認

```bash
./gradlew check  # → BUILD SUCCESSFUL
```

Checkstyle, SpotBugs, JUnit 5, Jacoco がすべてパス。

## CLAUDE.md (ルールファイル)

プロジェクトルートに `CLAUDE.md` を作成し、以下のルールを定義:

- Spring Boot 4.x 互換の書き方を強制（`SecurityFilterChain` のみ、`@MockitoBean` のみ等）
- DTO は record 必須、Entity だけ Lombok OK
- MapStruct 不使用
- ドメイン分割レイヤード + interface + impl パターン
- Flyway 必須（`ddl-auto` 禁止）

---

**→ この後のモノレポ化・フロントエンド・インフラ整備については [01-monorepo-and-infra-setup.md](01-monorepo-and-infra-setup.md) に記録**
