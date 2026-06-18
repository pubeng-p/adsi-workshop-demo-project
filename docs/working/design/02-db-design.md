# DB 設計の設計判断

ドメイン分析（`docs/design/01-domain-analysis.md`）をもとに DB 設計を行う。

---

## 1. テーブル命名規則

[Question] テーブル名・カラム名の命名規則はどちらにしますか？

- **A: snake_case（複数形）**: `employees`, `attendance_records`, `clock_in`。PostgreSQL の慣例に合う
- **B: snake_case（単数形）**: `employee`, `attendance_record`, `clock_in`。Entity 名と一致して分かりやすい

提案: **A（snake_case、複数形）**。PostgreSQL / Rails / Spring Data JDBC いずれも複数形が多い。

[Answer]

A

---

## 2. UUID の DB 型

[Question] UUID カラムの PostgreSQL 型はどちらにしますか？

- **A: `uuid` 型**: PostgreSQL ネイティブの UUID 型。16 バイト固定長でインデックス効率が良い
- **B: `varchar(36)` 型**: 文字列として保存。DB に依存しないが、36 バイトで効率が劣る

提案: **A（`uuid` 型）**。PostgreSQL を使うと決まっているので、ネイティブ型が適切。

[Answer]

A

---

## 3. Enum の DB 表現

Role（EMPLOYEE / ADMIN）や CorrectionStatus（PENDING / APPROVED / REJECTED）の保存方法。

[Question] Enum の DB 保存方法はどちらにしますか？

- **A: `varchar` で文字列保存**: `'EMPLOYEE'`, `'ADMIN'` のように文字列で保存。可読性が高い。JPA の `@Enumerated(EnumType.STRING)` で対応
- **B: PostgreSQL の `CREATE TYPE ... AS ENUM`**: DB レベルで制約できるが、Flyway での変更が面倒（ALTER TYPE の制約がある）
- **C: `smallint` で数値保存**: 省スペースだが、可読性が低い

提案: **A（varchar 文字列保存）** がシンプルで、DB を覗いたときも分かりやすい。CHECK 制約で値を制限すれば安全性も確保できる。

[Answer]

A

---

## 4. 監査カラム（created_at / updated_at）の自動設定

[Question] `created_at` / `updated_at` の設定方法はどちらにしますか？

- **A: JPA の `@CreatedDate` / `@LastModifiedDate`（Spring Data Auditing）**: アプリ側で制御。`@EnableJpaAuditing` を設定
- **B: DB のデフォルト値 + トリガー**: `DEFAULT now()` と `ON UPDATE` トリガーで DB 側で制御

提案: **A（Spring Data Auditing）**。アプリ層で完結し、テストでも制御しやすい。

[Answer]

A

---

## 5. 初期データ投入の方式

[Question] デモ用の初期データ投入は Flyway のどの機能を使いますか？

- **A: Flyway のバージョンマイグレーション（`V*__*.sql`）**: DDL と一緒に管理。環境ごとの差分はプロファイルで制御
- **B: Flyway のリピータブルマイグレーション（`R__*.sql`）**: 何度実行しても同じ結果。テストデータの更新が楽
- **C: Spring の `data.sql`（`spring.sql.init`）**: Flyway とは別管理。プロファイル `test` / `dev` で切り替え

提案: **A**。DDL と同じ Flyway で管理する方が一貫性がある。初期データ用の `V*__seed_data.sql` を作り、dev/test 環境でのみ適用する場合は Spring プロファイルで制御。

[Answer]

A