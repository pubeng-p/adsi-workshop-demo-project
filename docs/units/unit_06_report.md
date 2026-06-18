# Unit 06: 月次集計・帳票

月次集計の画面表示、CSV エクスポート、PDF 帳票出力。

## 依存関係

- 依存先: Unit 00（共通基盤）, Unit 02（社員 — 社員情報参照）, Unit 03（認証）, Unit 04（打刻 — 勤怠データ参照）
- 依存元: なし
- **Unit 05（修正）とは並列実装可能**

## ユーザーストーリー

- **RPT-01**: 管理者として、月次の勤怠集計を画面で確認したい
- **RPT-02**: 管理者として、月次集計を CSV でダウンロードしたい
- **RPT-03**: 管理者として、月次集計を PDF 帳票として出力したい

## API

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/api/reports/monthly` | 月次集計（JSON） | 管理者 |
| GET | `/api/reports/monthly/csv` | CSV エクスポート | 管理者 |
| GET | `/api/reports/monthly/pdf` | PDF 帳票出力 | 管理者 |

## 画面

| パス | ページ | コンポーネント |
|------|--------|--------------|
| `/admin/reports` | 月次集計 | `MonthSelector`, `DepartmentFilter`, `MonthlyReportTable`, `ExportButtons` |

## Backend 実装順序（TDD）

1. `ReportService` テスト → interface → 実装（attendance データの集計ロジック）
2. CSV 出力ロジック テスト → 実装
3. PDF 出力ロジック（JasperReports）テスト → 実装
4. `ReportController` テスト → 実装
5. 統合テスト

## Backend ファイル

```
packages/backend/src/
├── main/java/com/example/attendance/report/
│   ├── controller/ReportController.java
│   ├── dto/
│   │   ├── MonthlyReportResponse.java        (record)
│   │   └── EmployeeMonthlyRecord.java        (record)
│   └── service/
│       ├── ReportService.java                (interface)
│       ├── ReportServiceImpl.java
│       ├── CsvExportService.java
│       └── PdfExportService.java
├── main/resources/
│   └── reports/
│       └── monthly-report.jrxml              (JasperReports テンプレート)
└── test/java/com/example/attendance/report/
    ├── controller/ReportControllerTest.java
    └── service/
        ├── ReportServiceTest.java
        ├── CsvExportServiceTest.java
        └── PdfExportServiceTest.java
```

## Frontend ファイル

```
packages/frontend/src/features/report/
├── MonthlyReportTable.tsx
├── DepartmentFilter.tsx
├── ExportButtons.tsx
├── useReports.ts
└── report-api.ts

packages/frontend/src/app/(authenticated)/admin/reports/
└── page.tsx
```

## テストケース

### Backend

| テスト | 種類 | 内容 |
|--------|------|------|
| Service: 月次集計（全社） | Unit | 全社員の出勤日数・勤務時間・残業・欠勤を集計 |
| Service: 月次集計（部署フィルタ） | Unit | 指定部署のみ集計 |
| Service: 勤務時間計算 | Unit | WorkDuration と同じ計算ロジック（再利用） |
| Service: 欠勤日数計算 | Unit | 営業日 - 出勤日数（土日祝は除外） |
| CsvExport: CSV 生成 | Unit | 正しいヘッダーとデータ行 |
| CsvExport: CSV 文字コード | Unit | UTF-8 BOM 付き（Excel 対応） |
| PdfExport: PDF 生成 | Unit | JasperReports でバイト配列が返る |
| Controller: GET /api/reports/monthly | WebMvcTest | 200 + JSON |
| Controller: GET monthly（一般社員） | WebMvcTest | 403 |
| Controller: GET monthly/csv | WebMvcTest | 200 + text/csv ヘッダー |
| Controller: GET monthly/pdf | WebMvcTest | 200 + application/pdf ヘッダー |

### Frontend

| テスト | 種類 | 内容 |
|--------|------|------|
| MonthlyReportTable: 集計表示 | Component | 社員ごとの勤務データをテーブル表示 |
| DepartmentFilter: フィルタ切り替え | Component | 部署選択で再取得 |
| ExportButtons: CSV ダウンロード | Component | ボタンクリック → ファイルダウンロード |
| ExportButtons: PDF ダウンロード | Component | ボタンクリック → ファイルダウンロード |

## ビジネスルール

- 集計は attendance_records の月次データをもとに計算
- 勤務時間計算は Unit 04 の WorkDuration ロジックを再利用
- CSV は UTF-8 BOM 付き（日本語 Excel で文字化けしない）
- PDF は JasperReports で出力（テンプレートは `.jrxml`）
- 部署フィルタは任意。未指定なら全社員

## 完了条件

- [ ] 月次集計が画面に正しく表示される
- [ ] 部署フィルタが機能する
- [ ] CSV ダウンロードが動作し、内容が正しい
- [ ] PDF ダウンロードが動作し、帳票が正しくレンダリングされる
- [ ] 管理者のみアクセス可能
- [ ] Backend テストカバレッジ 80% 以上（report パッケージ）
