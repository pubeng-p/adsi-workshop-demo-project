# ドメイン分析の設計判断

要件定義をもとにドメインモデルを設計する。設計判断が必要な箇所を Q&A で整理する。

---

## 1. 打刻レコードの粒度

同日に「出勤→退勤→出勤→退勤」の複数回打刻を許可する要件（ATT-06）がある。

[Question] 打刻データのモデリングはどちらがよいですか？

- **A: 1出退勤ペア = 1レコード**: `attendance_records` テーブルに出勤時刻・退勤時刻を1行で管理。同日複数回打刻なら同じ日に複数行できる。シンプルで拡張しやすい
- **B: 1日1レコード + 打刻詳細テーブル**: 日次の親レコード + 打刻ペアの子テーブル。日次集計には便利だが、テーブル構成が複雑になる

提案: **A（1ペア = 1レコード）** がシンプルでおすすめ。日次集計は SQL の GROUP BY で対応できる。

[Answer]

A でいこう。

---

## 2. 打刻忘れの修正

FIX-01〜02 で「打刻忘れや誤りの修正を申請できる」とある。

[Question] 完全に打刻を忘れた日（レコードが存在しない日）の修正はどう扱いますか？

- **A: 修正申請で新規レコードも作成可能**: 修正申請時に「対象日に打刻がない場合は新規作成」として扱う。申請が承認されたら打刻レコードが作られる
- **B: 管理者が直接追加**: 打刻忘れは管理者に連絡して手動で追加してもらう（修正申請フローは既存レコードの修正のみ）
- **C: 社員が「打刻漏れ申請」として出勤・退勤時刻を入力 → 上長承認で新規レコード作成**: A に近いが、修正と打刻漏れを区別する

提案: **A** が自然。修正申請画面で「対象日」と「出勤/退勤時刻」を入力する形にすれば、既存レコードの修正も打刻忘れも同じフローで扱える。

[Answer]

修正申請で新規レコードも作成可能。

---

## 3. 修正申請と打刻レコードの関連

[Question] 修正申請は打刻レコードとどう紐づけますか？

- **A: 修正申請に修正後の値を持たせる**: `attendance_corrections` テーブルに `corrected_clock_in`, `corrected_clock_out` を持ち、承認時に `attendance_records` に反映する。打刻忘れの場合は承認時に新規レコードを作成
- **B: 修正申請は打刻レコードの FK を持ち、差分のみ記録**: 既存レコードへの参照 + 変更フィールドだけ記録。打刻忘れの扱いが複雑になる

提案: **A** がおすすめ。修正申請が「あるべき姿」を完全に保持するので、承認時の処理がシンプルになる。

[Answer]

A で。

---

## 4. ドメインパッケージの分割

CLAUDE.md に「ドメイン分割レイヤード」とある。

[Question] 以下のパッケージ分割案で問題ありませんか？

```
com.example.attendance
├── auth/           — 認証（ログイン/ログアウト）
│   ├── controller/
│   ├── service/
│   └── dto/
├── employee/       — 社員マスタ管理
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── department/     — 部署管理
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── attendance/     — 打刻・勤怠履歴
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── correction/     — 勤怠修正（申請・承認）
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
├── report/         — 月次集計・帳票出力
│   ├── controller/
│   ├── service/
│   └── dto/
└── common/         — 共通基盤
    ├── config/     — Security, CORS 等の設定
    ├── exception/  — 例外ハンドリング(@RestControllerAdvice)
    └── util/       — 共通ユーティリティ
```

ポイント:
- ドメインごとに controller / service / repository / entity / dto を配置
- `auth` は Spring Security の設定 + ログイン/ログアウトの Controller
- `report` は独自の Entity を持たず、`attendance` と `employee` のデータを集計する
- `common` はドメインに属さない横断的な機能

[Answer]

ドメイン分割レイヤードで。モダンな Java 開発のデモとしてはこちらが適切。

---

## 5. タイムゾーンの扱い

[Question] 打刻時刻の保存・表示のタイムゾーンはどうしますか？

- **A: DB は UTC 保存、表示は JST**: 国際化対応を考慮したベストプラクティス。ただしデモでは日本限定
- **B: JST 固定**: DB も表示も JST。デモなのでシンプルに

提案: **A（UTC 保存・JST 表示）** がおすすめ。Spring Boot + PostgreSQL ではそれが自然な構成で、`LocalDateTime` ではなく `OffsetDateTime` (or `Instant`) を使う形になる。ただしデモの簡易さを優先するなら B でもよい。

[Answer]

A で。

---

## 6. 社員IDの型

ユーザーストーリーでは「社員ID: 自動採番（UUID）」と記載がある。

[Question] UUID の生成方式はどちらにしますか？

- **A: UUID v4（ランダム）**: Java の `UUID.randomUUID()` で生成。実装がシンプル
- **B: UUID v7（時系列ソート可能）**: 生成時刻が含まれるため、ID でのソートが時系列順になる。DB のインデックス効率も良い。Java 21 では外部ライブラリが必要（例: `uuid-creator`）

提案: **A（UUID v4）** でデモには十分。

[Answer]

B にしよう。