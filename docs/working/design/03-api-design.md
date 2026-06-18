# API 設計の設計判断

---

## 1. API パスのプレフィックス

[Question] API のベースパスはどうしますか？

- **A: `/api/`**: シンプル。バージョニングが不要ならこれで十分
- **B: `/api/v1/`**: バージョン付き。将来の破壊的変更に備えられるが、デモでは不要かも

提案: **A（`/api/`）**。デモプロジェクトでバージョニングは不要。

[Answer]

A

---

## 2. ページネーション

社員一覧・勤怠履歴など、データが多くなりうるエンドポイントでの方式。

[Question] ページネーションの方式はどちらにしますか？

- **A: オフセットベース**: `?page=0&size=20`。Spring Data の `Pageable` でそのまま対応。シンプルで一般的
- **B: カーソルベース**: `?cursor=xxx&size=20`。大量データに強いが実装が複雑

提案: **A（オフセットベース）**。Spring Data の `Pageable` + `Page<T>` をそのまま使えてシンプル。デモ規模ではオフセットの性能問題は起きない。

[Answer]

A

---

## 3. エラーレスポンス形式

[Question] エラーレスポンスの JSON 形式はどちらにしますか？

- **A: RFC 9457 (Problem Details)**: Spring Boot 3.x がネイティブサポート。`application/problem+json` で返す。業界標準
- **B: カスタム形式**: プロジェクト独自のエラー形式を定義する

提案: **A（RFC 9457）**。Spring Boot 3.x の `ProblemDetail` クラスをそのまま使える。

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "出勤済みのため、再度出勤打刻はできません",
  "instance": "/api/attendance/clock-in",
  "errors": [
    { "field": "clockIn", "message": "..." }
  ]
}
```

[Answer]

A

---

## 4. OpenAPI 定義の方式

[Question] OpenAPI 仕様の管理方法はどちらにしますか？

- **A: コードファースト（SpringDoc）**: Controller のアノテーションから自動生成。Swagger UI で確認。実装と乖離しない
- **B: デザインファースト（YAML 手書き）**: 先に OpenAPI YAML を書き、実装はそれに合わせる。フロント・バック並行開発に向く
- **C: 両方**: 設計段階ではエンドポイント一覧 + リクエスト/レスポンス形式を設計ドキュメントに書き、実装時に SpringDoc で自動生成。設計ドキュメントが概要、Swagger UI が詳細仕様の役割

提案: **C**。設計フェーズでは `docs/design/` にエンドポイント一覧を書き、実装時に SpringDoc アノテーションで詳細を定義する。

[Answer]

C

---

## 5. 認証 API の設計

セッションベース認証（Spring Security）を使う。

[Question] ログイン API のリクエスト形式はどちらにしますか？

- **A: JSON ボディ**: `POST /api/auth/login` に `{"email": "...", "password": "..."}` を送る。SPA との相性が良い
- **B: フォームデータ**: Spring Security のデフォルト（`application/x-www-form-urlencoded`）。設定がシンプルだが、SPA からは少し不自然

提案: **A（JSON ボディ）**。Next.js フロントエンドとの相性が良い。Spring Security のカスタム `AuthenticationFilter` で対応。

[Answer]

A

