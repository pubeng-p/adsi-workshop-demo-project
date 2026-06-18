# 基盤整備設計

設計判断（`docs/working/design/05-infrastructure.md`）をもとに整理。

---

## ローカル開発環境

Docker Compose で PostgreSQL を起動する。

```yaml
# docker-compose.yml（ルートに配置）
services:
  db:
    image: postgres:17
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: attendance
      POSTGRES_USER: attendance
      POSTGRES_PASSWORD: attendance
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

ルートの `package.json` にコマンドを追加:
- `npm run db:up` → `docker compose up -d`
- `npm run db:down` → `docker compose down`

---

## テスト基盤

### テスト用 DB: Testcontainers

テスト実行時に Docker で PostgreSQL を自動起動・破棄する。本番と同じ DB エンジンで検証。

```groovy
// build.gradle
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:postgresql'
```

### テストプロファイル

- `application-test.yml` で Testcontainers の接続設定
- テストクラスには `@ActiveProfiles("test")` を付与

### カバレッジ: JaCoCo + Vitest

| 対象 | ツール | 閾値 | 実行 |
|------|--------|------|------|
| Backend | JaCoCo | 80% | `./gradlew jacocoTestReport` |
| Frontend | Vitest (istanbul) | 80% | `npm run test:coverage` |

CI でカバレッジが閾値を下回ったらビルド失敗にする。

---

## 静的解析

### Backend: Checkstyle + SpotBugs

```
packages/backend/
├── config/
│   ├── checkstyle/
│   │   └── checkstyle.xml          ← Google Java Style ベース
│   └── spotbugs/
│       └── spotbugs-exclude.xml    ← 除外パターン
```

```groovy
// build.gradle
plugins {
    id 'checkstyle'
    id 'com.github.spotbugs' version '6.x'
}

checkstyle {
    toolVersion = '10.x'
    configFile = file("config/checkstyle/checkstyle.xml")
}

spotbugs {
    effort = 'max'
    reportLevel = 'medium'
    excludeFilter = file("config/spotbugs/spotbugs-exclude.xml")
}
```

- Checkstyle: コーディング規約の準拠チェック
- SpotBugs: NullPointer、リソースリーク等のバグパターン検出
- どちらも `./gradlew build` に含まれるため CI で自動実行

### Frontend: Biome

ESLint + Prettier の代替。1ツールで lint + format。

```json
// biome.json
{
  "formatter": { "enabled": true },
  "linter": { "enabled": true }
}
```

- `npm run lint` → `biome check .`
- `npm run format` → `biome format --write .`

---

## ArchUnit ルール

`packages/backend/src/test/java/.../architecture/` に配置。

### 1. レイヤー依存ルール

Controller → Service → Repository の方向のみ許可。逆方向は禁止。

### 2. ドメイン間依存ルール

許可されたドメイン間依存のみ:

```
auth       → employee
attendance → employee
correction → employee, attendance
report     → employee, attendance
common     ← 全ドメインから参照可
```

上記以外のドメイン間依存（例: `department` → `correction`）は禁止。

### 3. Entity の独立性

Entity パッケージは他のレイヤー（Controller, Service）に依存しない。

### 4. DI ルール

`@Autowired` のフィールドインジェクション禁止。コンストラクタインジェクションのみ。

---

## Spring Security 設定

### 認証方式

- セッションベース認証（Spring Security）
- ログインは JSON ボディ（`POST /api/auth/login`）
- カスタム `AuthenticationFilter` で JSON リクエストを処理

### CSRF 対策

`CookieCsrfTokenRepository` による CSRF トークン方式:

1. サーバーが CSRF トークンを `XSRF-TOKEN` Cookie に設定
2. フロントエンド（Next.js）がリクエスト時に `X-XSRF-TOKEN` ヘッダーにトークンを付与
3. Spring Security がヘッダーと Cookie の値を照合

```java
// SecurityFilterChain の設定
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
)
```

フロントエンド側:
```typescript
// api-client.ts — fetch ラッパーで Cookie から XSRF-TOKEN を読み取りヘッダーに付与
```

### セッション設定

- タイムアウト: 30 分
- Cookie: `SameSite=Lax`, `HttpOnly`, `Secure`（本番のみ）

### CORS 設定

許可オリジンは環境変数 `ALLOWED_ORIGINS` で指定。

```yaml
# application.yml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS:http://localhost:3000}
```

---

## UUID v7

uuid-creator ライブラリで Entity のコンストラクタ生成。`persist()` 前に ID が確定する。

```groovy
// build.gradle
implementation 'com.github.f4b6a3:uuid-creator:6.x'
```

```java
@Entity
public class Employee {
    @Id
    private UUID id;

    protected Employee() {} // JPA 用

    public Employee(String name, ...) {
        this.id = UuidCreator.getTimeOrderedEpoch(); // UUID v7
        this.name = name;
    }
}
```

---

## 構造化ログ

### Logback JSON 出力

Logstash Logback Encoder で JSON 形式のログを出力。

```groovy
// build.gradle
implementation 'net.logstash.logback:logstash-logback-encoder:8.x'
```

```xml
<!-- logback-spring.xml -->
<configuration>
  <springProfile name="dev">
    <!-- 開発時はコンソールにプレーンテキスト -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
  </springProfile>

  <springProfile name="prod">
    <!-- 本番は JSON -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
  </springProfile>
</configuration>
```

### リクエストトレーシング

Micrometer Tracing で HTTP リクエストごとに `traceId` を自動付与。

```groovy
// build.gradle
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
```

---

## Spring Boot Actuator

```groovy
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
  endpoint:
    health:
      show-details: always
  info:
    git:
      mode: full
```

Actuator エンドポイントは Spring Security で認証不要に設定。本番ではネットワーク制限で保護。

---

## CI/CD パイプライン（GitHub Actions）

### PR 時（ビルド + テスト）

```
トリガー: Pull Request
ステップ:
  1. Backend: ./gradlew build（コンパイル + Checkstyle + SpotBugs + テスト + JaCoCo）
  2. Frontend: npm ci → biome check → npm run test:coverage → npm run build
```

### main マージ時（dev デプロイ）

```
トリガー: push to main
ステップ:
  1. ビルド + テスト（上記と同じ）
  2. Docker イメージビルド + ECR push
  3. CDK deploy（dev 環境）
```

### タグ時（prod デプロイ）

```
トリガー: tag push（例: v1.0.0）
ステップ:
  1. ビルド + テスト
  2. Docker イメージビルド + ECR push
  3. CDK deploy（prod 環境）
```
