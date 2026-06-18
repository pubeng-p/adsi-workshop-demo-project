# Unit 03: 認証

セッションベースのログイン/ログアウト。Spring Security によるアクセス制御の実装。

## 依存関係

- 依存先: Unit 00（共通基盤）, Unit 02（社員 — ユーザー情報参照）
- 依存元: Unit 04（打刻）, Unit 05（修正）, Unit 06（集計）
- **この Unit の完了後、Unit 00 で `permitAll` にしていた SecurityFilterChain を本来の権限設定に切り替える**

## ユーザーストーリー

- **AUTH-01**: 社員として、メールアドレスとパスワードでログインしたい
- **AUTH-02**: 社員として、ログアウトしたい
- **AUTH-03**: 退職済みの社員はログインできないようにしたい

## API

| メソッド | パス | 説明 | 認証 |
|---------|------|------|------|
| POST | `/api/auth/login` | ログイン | 不要 |
| POST | `/api/auth/logout` | ログアウト | 必要 |
| GET | `/api/auth/me` | ログイン中ユーザー情報 | 必要 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/login` | ログイン | `LoginForm` |

## Backend 実装順序（TDD）

1. `UserDetailsService` 実装（Employee を Spring Security の UserDetails に変換）
2. カスタム `JsonAuthenticationFilter`（JSON ボディでログイン）
3. `SecurityFilterChain` を本番設定に切り替え（権限マトリクス適用）
4. CSRF 設定（`CookieCsrfTokenRepository`）
5. CORS 設定（環境変数 `ALLOWED_ORIGINS`）
6. `AuthController`（`/me` エンドポイント）
7. ログイン成功/失敗ハンドラ
8. セッション設定（タイムアウト 30 分）
9. 統合テスト

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/auth/
│   ├── controller/AuthController.java
│   ├── dto/
│   │   ├── LoginRequest.java             (record)
│   │   └── AuthUserResponse.java         (record)
│   └── service/
│       ├── AuthService.java              (interface)
│       └── AuthServiceImpl.java
├── main/java/com/example/attendance/common/config/
│   ├── SecurityConfig.java               (更新: 権限マトリクス適用)
│   ├── CorsConfig.java                   (更新: ALLOWED_ORIGINS)
│   └── security/
│       ├── JsonAuthenticationFilter.java
│       ├── CustomUserDetailsService.java
│       ├── LoginSuccessHandler.java
│       ├── LoginFailureHandler.java
│       ├── CustomAuthenticationEntryPoint.java
│       ├── CustomAccessDeniedHandler.java
│       └── SpaCsrfTokenRequestHandler.java
└── test/java/com/example/attendance/auth/
    ├── controller/AuthControllerTest.java
    ├── service/AuthServiceTest.java
    └── security/
        ├── SecurityConfigTest.java
        └── JsonAuthenticationFilterTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/auth/
├── LoginForm.tsx
├── useAuth.ts
└── auth-api.ts

packages/frontend/src/app/login/
└── page.tsx                              (更新)

packages/frontend/src/app/(authenticated)/
└── layout.tsx                            (更新: 認証チェック、リダイレクト)

packages/frontend/src/components/layout/
├── Sidebar.tsx                           (更新: ロール別メニュー表示)
└── Header.tsx                            (更新: ユーザー名、ログアウトボタン)

packages/frontend/src/lib/
└── api-client.ts                         (更新: CSRF トークン付与、401 リダイレクト)
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| UserDetailsService: 正常ユーザー | Unit | メールで社員を取得し UserDetails を返す |
| UserDetailsService: 存在しないメール | Unit | UsernameNotFoundException |
| UserDetailsService: 退職済み社員 | Unit | アカウント無効として扱う |
| SecurityConfig: 未認証→保護エンドポイント | 統合 | 401 Unauthorized |
| SecurityConfig: 認証済み→権限なし | 統合 | 403 Forbidden |
| SecurityConfig: 管理者→管理者API | 統合 | 200 OK |
| SecurityConfig: 一般→管理者API | 統合 | 403 Forbidden |
| SecurityConfig: 上長→承認API | 統合 | 200 OK |
| Login: 正常ログイン | 統合 | 200 + ユーザー情報 + セッション Cookie |
| Login: パスワード不一致 | 統合 | 401 |
| Login: 退職済み社員 | 統合 | 401 |
| Logout: 正常ログアウト | 統合 | 204 + セッション破棄 |
| GET /me: 認証済み | WebMvcTest | 200 + ユーザー情報 |
| GET /me: 未認証 | WebMvcTest | 401 |
| CSRF: トークンなしで POST | 統合 | 403 |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| LoginForm: 正常ログイン | Component | 入力 → 送信 → リダイレクト |
| LoginForm: ログイン失敗 | Component | エラーメッセージ表示 |
| LoginForm: バリデーション | Component | 空入力でエラー |
| useAuth: 認証状態管理 | Hook | ログイン/ログアウトで状態変化 |
| Sidebar: ロール別メニュー | Component | ADMIN → 管理メニュー表示、EMPLOYEE → 非表示 |
| Sidebar: 上長メニュー | Component | isManager=true → 承認メニュー表示 |

## 権限マトリクス（SecurityFilterChain で実装）

```
permitAll:
  POST /api/auth/login
  GET  /actuator/**

authenticated (全ロール):
  POST /api/auth/logout
  GET  /api/auth/me
  GET  /api/departments
  POST /api/attendance/clock-in
  POST /api/attendance/clock-out
  GET  /api/attendance/today
  GET  /api/attendance/history
  POST /api/corrections
  GET  /api/corrections

上長 (isManager = true):
  GET   /api/attendance/team
  GET   /api/corrections/pending
  PATCH /api/corrections/{id}/approve
  PATCH /api/corrections/{id}/reject

管理者 (ADMIN):
  /api/employees/**
  POST /api/departments
  PUT  /api/departments/{id}
  GET  /api/attendance/all
  /api/reports/**
```

## 完了条件

- [ ] メールアドレス + パスワードでログインできる
- [ ] ログイン後にセッション Cookie が発行される
- [ ] 退職済み社員がログインできない
- [ ] 未認証で保護エンドポイントにアクセスすると 401 が返る
- [ ] 権限不足で 403 が返る
- [ ] CSRF トークンが正しく機能する
- [ ] フロントエンドのログイン画面が動作する
- [ ] サイドバーがロール・上長フラグに応じてメニューを出し分ける
- [ ] Backend テストカバレッジ 80% 以上（auth パッケージ + security 設定）
