# 03: 要件定義

## プロンプト

> `docs/project-brief.md` をもとに、要件定義を進めていきたい

> Question Answer タグを含むドキュメント内でユーザーが Answer を埋めながら進めていくイメージでやりたい。そういう作業用ドキュメントは `docs/working/requirements/` みたいな場所に置くイメージでやろう。rules の更新もよろしく

## やったこと

### 1. 作業ドキュメントの仕組み整備

- `docs/working/requirements/` ディレクトリを作成
- `.claude/rules/development-process.md` に作業ドキュメント（`docs/working/`）の運用ルールを追記
- `CLAUDE.md` に `docs/working/` の説明を追加

### 2. 未決事項の Q&A

`docs/working/requirements/01-undecided-items.md` を作成し、project-brief.md の未決事項 7 件 + 補足質問 8 件を `[Question]` / `[Answer]` 形式で整理。ユーザーが `[Answer]` を直接ファイルに記入して仕様を確定した。

### 3. 確定した仕様

| 項目 | 決定内容 |
| --- | --- |
| 認証方式 | セッションベース |
| 社員マスタ | 初期データ + 管理画面 |
| 打刻方法 | ボタン押下のみ |
| 承認フロー | 社員が申請 → 上長が承認 |
| 休憩時間 | 固定控除（労基法準拠） |
| 残業定義 | 1 日 8h 超過分 |
| 組織構造 | フラット（1 階層） |
| 月次集計 | 画面 + CSV + PDF（JasperReports） |
| ロール体系 | 一般社員 / 上長（部署フラグ）/ 管理者（人事） |
| 上長不在時 | 考慮しない（デモスコープ外） |
| 管理者と上長の兼任 | 可能 |

### 4. ユーザーストーリー

確定した仕様をもとに `docs/requirements/01-user-stories.md` を作成。ロール別にストーリーを整理し、社員マスタの項目定義・勤務時間計算ルール・初期データ仕様も含めた。

### 5. project-brief.md 更新

未決事項セクションを「決定事項」に更新。

## 最終構成

```
docs/
├── project-brief.md              # 更新: 未決事項 → 決定事項
├── requirements/
│   └── 01-user-stories.md         # 新規: ユーザーストーリー
├── working/
│   └── requirements/
│       └── 01-undecided-items.md   # 新規: Q&A 作業ドキュメント
├── design/                        # 空（次のステップで使用）
├── units/                         # 空（次のステップで使用）
└── path/
    ├── 00-environment-setup.md
    ├── 01-monorepo-and-infra-setup.md
    ├── 02-rules-and-process.md
    └── 03-requirements-definition.md  # 本ファイル
```
