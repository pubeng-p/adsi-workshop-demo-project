# Attendance Demo Project

勤怠管理デモアプリ。モノレポ構成。

## Claude Code ハーネス

`.claude/` に rules / skills / agents を同梱（外部プラグイン不要）。詳細は [.claude/README.md](.claude/README.md)。

| やりたいこと | 参照先 |
|-------------|--------|
| 起動（ローカル / SageMaker） | `.claude/skills/dev-environment/SKILL.md` |
| SageMaker プレビュー設定 | `.claude/skills/sagemaker-code-editor/SKILL.md` |
| SageMaker から AWS デプロイ | `.claude/skills/sagemaker-aws-deploy/SKILL.md` |
| コーディング規約 | `.claude/rules/` |
| 開発の全体像（SDD / 仕様駆動開発） | `.claude/rules/common/development-process.md` |
| Issue 運用（要求/設計の永続化） | `.claude/rules/common/issue-workflow.md` |
| 要求仕様 | `.claude/skills/requirements/SKILL.md` |
| 設計 | `.claude/skills/design/SKILL.md` |
| 作業分割（UoW） | `.claude/skills/work-decomposition/SKILL.md` |
| TDD 実装 | `.claude/skills/tdd-implementation/SKILL.md` |
| コードレビュー | `.claude/skills/multi-agent-review/SKILL.md` |

> **スキルの起動方針（ワークショップ）**: SDD 工程スキル（`requirements` / `design` / `work-decomposition` / `tdd-implementation`）は **明示的に指定されたときのみ** 使用し、**自動では起動しない**。前半は参加者が自分でプロンプトを入力して各工程を体験し、後半で必要に応じてスキル名を指定して呼び出す。

### SageMaker クイックリファレンス

```bash
npm run dev:sagemaker        # 起動
npm run dev:sagemaker:stop   # 停止
```

アクセス: PORTS タブの地球儀 → URL の `ports` を `absports` に置換（例: `.../absports/3000/`）

## パッケージ構成

- `packages/backend/` — Spring Boot 3.x (Java 21)
- `packages/frontend/` — Next.js (TypeScript)
- `packages/infra/` — AWS CDK (TypeScript)。dev/prod
- `docs/path/` — デモの過程ドキュメント
- `docs/working/` — 要件・設計の Q&A 作業ドキュメント

## セットアップ

```bash
npm run setup
```

## docs/path ルール

デモの過程を `docs/path/` に番号付きファイル（`00-xxx.md`）で記録する。
新ステップに進んだら新ファイルを作成。プロンプト・やったこと・つまずき・最終構成を含める。

---

## アーキテクチャ・命名・スタイル早見表

> 詳細は `.claude/rules/` を参照。ここでは実装時に即参照するパターンを集約。

### パッケージ構造（Backend）

ドメインファースト（Vertical Slice）。各ドメインの下にレイヤーを配置:

```
com.example.attendance/
  <domain>/              # attendance, correction, employee, department, auth, report
    controller/
    domain/              # Value Object, Enum
    dto/
    entity/
    repository/
    service/
  common/                # config, exception, shared infra
```

### Entity パターン

```java
@Entity
@Table(name = "table_name")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityName {

    @Id
    private UUID id;                          // UuidCreator.getTimeOrderedEpoch() で生成

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_column", nullable = false)
    private OtherEntity other;

    @Column(nullable = false)
    private LocalDate someDate;

    @Column(length = 200)                     // VARCHAR 長は明示
    private String optionalField;

    @Column(nullable = false)
    @Builder.Default
    private boolean flag = false;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- Lombok アノテーション順序: `@Data` → `@NoArgsConstructor` → `@AllArgsConstructor` → `@Builder`
- ID は自動採番なし。Service 層で `UuidCreator.getTimeOrderedEpoch()` を呼ぶ
- `@Version` による楽観ロック標準
- JPA Auditing (`@CreatedDate` / `@LastModifiedDate`) で自動タイムスタンプ

### DTO パターン

```java
// Response — static from() ファクトリ
public record XxxResponse(UUID id, String name) {
    public static XxxResponse from(XxxEntity entity) {
        return new XxxResponse(entity.getId(), entity.getName());
    }
}

// Request — Bean Validation 直付け、メッセージは日本語
public record XxxRequest(
    @NotNull @Size(max = 200, message = "200文字以内で入力してください") String field
) {}
```

- DTO は必ず `record`。Lombok `@Data` 禁止
- MapStruct 禁止 — record コンストラクタで手動マッピング

### Service パターン

```java
@Slf4j
@Service
@Transactional(readOnly = true)            // クラスレベルは readOnly
public class XxxServiceImpl implements XxxService {

    private final XxxRepository repository;
    private final Clock clock;              // テスト可能な時刻注入

    public XxxServiceImpl(XxxRepository repository, Clock clock) { // 明示コンストラクタ
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional                          // 書き込みメソッドのみ上書き
    public XxxResponse create(...) { ... }
}
```

- `@RequiredArgsConstructor` 不使用 — コンストラクタは明示的に書く
- 例外: `EntityNotFoundException`（404）, `ResponseStatusException(CONFLICT)`（409）
- UUID 生成は Service 層で実行

### Controller パターン

```java
@RestController
@RequestMapping("/api/resource")
public class XxxController {
    // コンストラクタインジェクション
    // @ResponseStatus(HttpStatus.CREATED) を POST に付与
    // 戻り値は DTO 直接（ResponseEntity は使わない）
    // @Valid @RequestBody / @RequestParam / @PathVariable
}
```

- 例外ハンドリングは `GlobalExceptionHandler` に集約（Controller には書かない）
- RFC 7807 ProblemDetail 形式でエラーレスポンス

### Repository パターン

- `extends JpaRepository<Entity, UUID>`
- Spring Data 命名規約メソッド（`findByXxxAndYyy`）
- 動的検索は `JpaSpecificationExecutor` + static `Specification` ファクトリ

### テストパターン（Backend）

| レベル | アノテーション | 用途 |
|--------|--------------|------|
| Unit (Service) | `@ExtendWith(MockitoExtension.class)` | ビジネスロジック |
| Unit (Domain) | なし（plain JUnit 5） | 純粋ドメインロジック |
| Slice (Controller) | `@WebMvcTest` | HTTP 層 |
| Slice (Repository) | `@DataJpaTest` + `@Import(JpaAuditingConfig.class)` | DB クエリ |
| Integration | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` | E2E |
| Architecture | ArchUnit `@AnalyzeClasses` | レイヤー依存方向 |

```java
// メソッド命名: methodUnderTest_condition_expectedBehavior
@Test
@DisplayName("出勤打刻: 未打刻の社員が打刻するとレコードが作成される")
void clockIn_notYetClockedIn_createsRecord() { ... }
```

- AAA パターン: Arrange → Act → Assert
- AssertJ のみ（`assertThat`, `assertThatThrownBy`）
- `@Mock` + `@ExtendWith(MockitoExtension.class)`; `@MockitoBean` (Spring Boot 4 対応)
- `@ActiveProfiles("test")` 必須
- テストDB: H2 (`MODE=PostgreSQL`, `ddl-auto: create-drop`)

### フロントエンド構成

```
src/
  app/                    # Next.js App Router (pages)
  components/             # 共有コンポーネント (FormDialog, DataTable, etc.)
  components/ui/          # shadcn/ui v4 (Base UI ベース)
  features/<domain>/      # Feature-sliced
    <domain>-api.ts       # API 関数 + interface 定義
    use<Domain>.ts        # React Query hooks
    <Component>.tsx       # UI コンポーネント
    <Component>.test.tsx  # テスト（コロケーション）
  hooks/                  # 共有 hooks
  lib/                    # apiClient, utils, validators
  types/                  # 共有型
```

### フロントエンドパターン

```typescript
// API: apiClient を使う。型は同ファイルで interface 定義
export function clockIn(employeeId: string): Promise<AttendanceRecordResponse> {
  return apiClient.post<AttendanceRecordResponse>(
    `/api/attendance/clock-in?employeeId=${employeeId}`,
  );
}

// Hook: query key は const tuple。mutation は onSuccess で invalidate + toast
const KEY = ["domain", "sub"] as const;
export function useXxx() {
  const { user } = useAuth();
  return useMutation({
    mutationFn: (args) => apiFunction(user!.id, args),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEY });
      toast.success("成功メッセージ");
    },
    onError: () => { toast.error("失敗メッセージ"); },
  });
}

// Component: named export, interface で Props 定義, "use client" ディレクティブ
"use client";
interface XxxProps { ... }
export function Xxx({ ... }: XxxProps) { ... }
```

- UI ライブラリ: shadcn/ui v4 + @base-ui/react + Tailwind CSS v4 + lucide-react
- フォーム: react-hook-form 不使用。`useState` + `<form onSubmit>` + 手動バリデーション
- ダイアログ: `<FormDialog>` で統一（title, onSubmit, children）
- `any` 禁止。`unknown` + 型ガード
- テスト: Vitest + Testing Library。説明は日本語。UI プリミティブは vi.mock
