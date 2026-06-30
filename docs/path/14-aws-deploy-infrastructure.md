# 14: AWS デプロイ基盤の構築

## プロンプト

> AWS にデプロイできるようにするには何を整えるんだっけ
> 全部やろう。静的で
> RDS って Aurora Serverless とかでいけない？

## 選択肢への回答

- フロントエンド配信方式 → **S3 + CloudFront（静的エクスポート）** を選択。SSR 機能は使っていないため
- DB → **Aurora Serverless v2** を選択。使わない時間帯は 0 ACU でほぼ無料、本番でも自動スケール
- セッション管理 → **ALB Sticky Session** で暫定対応。将来的に Spring Session + DynamoDB で外出し予定
- CORS → 初回デプロイ後に CloudFront ドメインに差し替える（循環参照で CDK 内では解決不可）

## やったこと

### 1. Backend Dockerfile 作成

`packages/backend/Dockerfile` を multi-stage build で作成：

```dockerfile
FROM eclipse-temurin:21-jdk AS build  # ビルドステージ
FROM eclipse-temurin:21-jre           # 実行ステージ（JRE のみ）
```

- `.dockerignore` も追加（`.gradle`, `build` 等を除外）
- JRE イメージには curl がないため、コンテナヘルスチェックは ALB に委譲

### 2. application-prod.yaml 整備

DB 接続情報を環境変数で受け取るように更新：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME:attendance}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

ECS タスク定義で `DB_HOST` 等を設定、`DB_USERNAME` / `DB_PASSWORD` は Secrets Manager から注入。

### 3. Next.js 静的エクスポート対応

`next.config.ts` を修正：
- 開発時（`next dev`）→ rewrites で `/api/*` を `localhost:8080` にプロキシ
- ビルド時（`next build`）→ `output: "export"` で静的ファイル生成

全15ページが静的生成されることを確認。`next/image` や動的ルートは未使用のため問題なし。

### 4. CDK スタック実装

`packages/infra/lib/attendance-stack.ts` を全面実装：

```
CloudFront (HTTPS)
├── /* → S3 (Next.js 静的ファイル)
│        └─ SPA routing CloudFront Function
│           (/dashboard → /dashboard.html)
└── /api/* → ALB (HTTP) → ECS Fargate (Spring Boot)
                                │
                          Aurora Serverless v2
                          (0〜2 ACU, auto-scale)
```

リソース一覧：
- **VPC**: 2 AZ、NAT Gateway 1台
- **Aurora Serverless v2**: PostgreSQL 17、0〜2 ACU、dev は DESTROY / prod は RETAIN
- **Secrets Manager**: DB 認証情報を自動生成
- **ECR**: バックエンドイメージ格納（ライフサイクル: 最大5イメージ）
- **ECS Fargate**: 0.25 vCPU / 512 MB、private subnet
- **ALB**: public subnet、Sticky Session (1時間)
- **S3**: フロントエンド静的ファイル、OAC でアクセス制御
- **CloudFront**: S3 + ALB をオリジンに統合、SPA ルーティング用 Function 付き

### 5. GitHub Actions デプロイパイプライン

`.github/workflows/deploy.yml` を作成：

```
test → deploy
  test: Backend check + Frontend test
  deploy: ECR push → CDK deploy → S3 sync → CloudFront invalidation
```

- `push to main` で dev 環境に自動デプロイ
- `workflow_dispatch` で手動トリガー（dev / prod 選択可）
- OIDC で AWS 認証（`id-token: write` パーミッション）

### 6. ALB Sticky Session 追加

Fargate でもタスクが複数ある場合やタスク再起動時にセッションが消える問題に対応：
- ALB TargetGroup に `stickinessCookieDuration: 1時間` を設定
- 暫定対応。将来的に Spring Session + DynamoDB に移行予定

## つまずき

### ECS ヘルスチェックの curl 問題

`eclipse-temurin:21-jre` イメージに curl がないため、`CMD-SHELL curl -f ...` のヘルスチェックが動かない。ALB ターゲットグループのヘルスチェック（`/actuator/health`）に委譲して解決。

### CORS の循環参照

CDK スタック内で CloudFront → ALB → ECS → CloudFront ドメイン名を参照しようとすると循環参照になる。暫定的に `CORS_ALLOWED_ORIGINS: "*"` とし、初回デプロイ後に CloudFront ドメインに差し替える運用とした。

## デプロイ前に必要な設定

### 1. AWS アカウント側

```bash
# CDK Bootstrap（対象アカウント・リージョンで1回だけ）
npx cdk bootstrap aws://ACCOUNT_ID/ap-northeast-1
```

### 2. GitHub OIDC 連携

AWS 側で GitHub Actions 用の OIDC プロバイダーと IAM ロールを作成する：

#### 2-1. OIDC プロバイダー登録

IAM > ID プロバイダー > プロバイダを追加：
- プロバイダのタイプ: OpenID Connect
- プロバイダの URL: `https://token.actions.githubusercontent.com`
- 対象者: `sts.amazonaws.com`

#### 2-2. IAM ロール作成

信頼ポリシー：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:d-experts/adsi-workshop-demo-project:*"
        }
      }
    }
  ]
}
```

必要な権限ポリシー：
- `AdministratorAccess`（デモ用。本番では CDK deploy に必要な最小権限に絞る）

#### 2-3. GitHub リポジトリ設定

Settings > Environments > `dev` を作成：
- `AWS_DEPLOY_ROLE_ARN` = 作成した IAM ロールの ARN

prod 環境も必要であれば同様に `prod` Environment を作成。

### 3. 初回デプロイ手順

```bash
# 1. CDK deploy（ECR リポジトリ等のインフラ作成）
cd packages/infra && npx cdk deploy --context env=dev

# 2. バックエンドイメージを push
cd packages/backend
docker build -t $(aws ecr describe-repositories --repository-names attendance-backend-dev --query 'repositories[0].repositoryUri' --output text):latest .
aws ecr get-login-password | docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.ap-northeast-1.amazonaws.com
docker push ECR_URI:latest

# 3. ECS サービス更新
aws ecs update-service --cluster CLUSTER_NAME --service SERVICE_NAME --force-new-deployment

# 4. フロントエンド配信
cd packages/frontend && npm run build
aws s3 sync out/ s3://BUCKET_NAME/ --delete

# 5. CORS 差し替え（CloudFront ドメイン確定後）
# CDK の CORS_ALLOWED_ORIGINS を CloudFront ドメインに更新して再デプロイ
```

以降は `main` に push すれば GitHub Actions が自動デプロイする。

## 最終構成（追加・変更ファイル）

```
.github/workflows/deploy.yml          ← CI/CD パイプライン
packages/backend/
  Dockerfile                           ← multi-stage build
  .dockerignore                        ← ビルドコンテキスト除外
  src/main/resources/
    application-prod.yaml              ← DB 環境変数設定
packages/frontend/
  next.config.ts                       ← output: "export" 追加
packages/infra/lib/
  attendance-stack.ts                  ← 全面実装
docs/design/
  05-infrastructure.md                 ← 改善項目セクション追加
```
