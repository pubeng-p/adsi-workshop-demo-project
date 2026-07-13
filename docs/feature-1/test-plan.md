# 打刻メモ機能 — テスト計画 (Red テストケース)

> TDD Red フェーズ: 以下のテストはすべて **実装前に書く** ためのもの。
> 実装が存在しないため、最初の実行ではすべて失敗（Red）する。

---

## 1. Backend — Service テスト

**ファイル:** `packages/backend/src/test/java/com/example/attendance/attendance/service/AttendanceServiceTest.java`（既存に追加）

既存の `@BeforeEach` で `service = new AttendanceServiceImpl(attendanceRepository, employeeRepository, clock)` を構築しているが、`MemoEditHistoryRepository` を追加する必要あり:

```java
@Mock
private MemoEditHistoryRepository memoEditHistoryRepository;

@BeforeEach
void setUp() {
    var clock = Clock.fixed(FIXED_INSTANT, ZONE_TOKYO);
    service = new AttendanceServiceImpl(attendanceRepository, employeeRepository, memoEditHistoryRepository, clock);
    // ... 既存の department/employee セットアップ ...
}
```

### 1.1 出勤打刻 — メモ関連

```java
@Nested
@DisplayName("出勤打刻 — メモ")
class ClockInWithMemo {

    @Test
    @DisplayName("メモ付きで出勤打刻するとメモがレコードに保存される")
    void clockIn_withMemo_savesMemoOnRecord() {
        // Arrange
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.clockIn(employee.getId(), "在宅勤務");

        // Assert
        var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getMemo()).isEqualTo("在宅勤務");
        assertThat(result.memo()).isEqualTo("在宅勤務");
    }

    @Test
    @DisplayName("メモがnullで出勤打刻するとメモはnullで保存される")
    void clockIn_withNullMemo_savesNullMemo() {
        // Arrange
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.clockIn(employee.getId(), null);

        // Assert
        var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getMemo()).isNull();
        assertThat(result.memo()).isNull();
    }

    @Test
    @DisplayName("空文字のメモで出勤打刻するとメモは空文字で保存される")
    void clockIn_withEmptyMemo_savesEmptyMemo() {
        // Arrange
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.clockIn(employee.getId(), "");

        // Assert
        var captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getMemo()).isEmpty();
    }
}
```

### 1.2 退勤打刻 — メモ関連

```java
@Nested
@DisplayName("退勤打刻 — メモ")
class ClockOutWithMemo {

    @Test
    @DisplayName("メモ付きで退勤打刻するとメモが上書きされる")
    void clockOut_withMemo_overwritesMemo() {
        // Arrange
        var openRecord = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .workDate(TODAY_TOKYO)
                .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                .memo("出勤時メモ")
                .build();
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                .thenReturn(Optional.of(openRecord));
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.clockOut(employee.getId(), "退勤時メモに変更");

        // Assert
        assertThat(result.memo()).isEqualTo("退勤時メモに変更");
    }

    @Test
    @DisplayName("メモがnullで退勤打刻すると既存メモは変更されない")
    void clockOut_withNullMemo_preservesExistingMemo() {
        // Arrange
        var openRecord = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .workDate(TODAY_TOKYO)
                .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                .memo("元のメモ")
                .build();
        when(attendanceRepository.findByEmployeeIdAndWorkDateAndClockOutIsNull(employee.getId(), TODAY_TOKYO))
                .thenReturn(Optional.of(openRecord));
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.clockOut(employee.getId(), null);

        // Assert
        assertThat(result.memo()).isEqualTo("元のメモ");
    }
}
```

### 1.3 メモ更新

```java
@Nested
@DisplayName("メモ更新")
class UpdateMemo {

    @Test
    @DisplayName("正常系: メモを更新し編集履歴が作成される")
    void updateMemo_validRequest_updatesAndCreatesHistory() {
        // Arrange
        var recordId = UUID.randomUUID();
        var record = AttendanceRecord.builder()
                .id(recordId)
                .employee(employee)
                .workDate(TODAY_TOKYO)
                .clockIn(FIXED_INSTANT)
                .memo("旧メモ")
                .version(0L)
                .build();
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(memoEditHistoryRepository.save(any(MemoEditHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.updateMemo(recordId, employee.getId(), "新メモ", 0L);

        // Assert
        assertThat(result.memo()).isEqualTo("新メモ");

        var historyCaptor = ArgumentCaptor.forClass(MemoEditHistory.class);
        verify(memoEditHistoryRepository).save(historyCaptor.capture());
        var history = historyCaptor.getValue();
        assertThat(history.getOldMemo()).isEqualTo("旧メモ");
        assertThat(history.getNewMemo()).isEqualTo("新メモ");
        assertThat(history.getEditor().getId()).isEqualTo(employee.getId());
        assertThat(history.getEditedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("メモをnullに更新できる（メモ削除）")
    void updateMemo_setToNull_clearssMemo() {
        // Arrange
        var recordId = UUID.randomUUID();
        var record = AttendanceRecord.builder()
                .id(recordId)
                .employee(employee)
                .workDate(TODAY_TOKYO)
                .clockIn(FIXED_INSTANT)
                .memo("既存メモ")
                .version(0L)
                .build();
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(memoEditHistoryRepository.save(any(MemoEditHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = service.updateMemo(recordId, employee.getId(), null, 0L);

        // Assert
        assertThat(result.memo()).isNull();

        var historyCaptor = ArgumentCaptor.forClass(MemoEditHistory.class);
        verify(memoEditHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOldMemo()).isEqualTo("既存メモ");
        assertThat(historyCaptor.getValue().getNewMemo()).isNull();
    }

    @Test
    @DisplayName("存在しないレコードIDで404エラー")
    void updateMemo_nonExistentRecord_throwsNotFound() {
        // Arrange
        var nonExistentId = UUID.randomUUID();
        when(attendanceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.updateMemo(nonExistentId, employee.getId(), "メモ", 0L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("存在しない編集者IDで404エラー")
    void updateMemo_nonExistentEditor_throwsNotFound() {
        // Arrange
        var recordId = UUID.randomUUID();
        var record = AttendanceRecord.builder()
                .id(recordId)
                .employee(employee)
                .workDate(TODAY_TOKYO)
                .clockIn(FIXED_INSTANT)
                .version(0L)
                .build();
        var unknownEditorId = UUID.randomUUID();
        when(attendanceRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(employeeRepository.findById(unknownEditorId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.updateMemo(recordId, unknownEditorId, "メモ", 0L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
```

### 1.4 メモ編集履歴取得

```java
@Nested
@DisplayName("メモ編集履歴取得")
class GetMemoEditHistory {

    @Test
    @DisplayName("編集履歴が降順で返される")
    void getMemoEditHistory_multipleEdits_returnsDescending() {
        // Arrange
        var recordId = UUID.randomUUID();
        var history1 = MemoEditHistory.builder()
                .id(UUID.randomUUID())
                .attendanceRecord(AttendanceRecord.builder().id(recordId).build())
                .editor(employee)
                .oldMemo(null)
                .newMemo("初回メモ")
                .editedAt(Instant.parse("2025-01-15T01:00:00Z"))
                .build();
        var history2 = MemoEditHistory.builder()
                .id(UUID.randomUUID())
                .attendanceRecord(AttendanceRecord.builder().id(recordId).build())
                .editor(employee)
                .oldMemo("初回メモ")
                .newMemo("更新メモ")
                .editedAt(Instant.parse("2025-01-15T02:00:00Z"))
                .build();
        when(memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(recordId))
                .thenReturn(List.of(history2, history1));

        // Act
        var result = service.getMemoEditHistory(recordId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).newMemo()).isEqualTo("更新メモ");
        assertThat(result.get(0).editorName()).isEqualTo("田中太郎");
        assertThat(result.get(1).newMemo()).isEqualTo("初回メモ");
    }

    @Test
    @DisplayName("編集履歴がない場合は空リストを返す")
    void getMemoEditHistory_noHistory_returnsEmptyList() {
        // Arrange
        var recordId = UUID.randomUUID();
        when(memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(recordId))
                .thenReturn(List.of());

        // Act
        var result = service.getMemoEditHistory(recordId);

        // Assert
        assertThat(result).isEmpty();
    }
}
```

---

## 2. Backend — Controller テスト

**ファイル:** `packages/backend/src/test/java/com/example/attendance/attendance/controller/AttendanceControllerTest.java`（既存に追加）

注: `AttendanceRecordResponse` のコンストラクタに `memo` が追加されるため、既存テストも修正が必要（6引数に変更）。

### 2.1 出勤打刻 — メモ付きリクエスト

```java
@Test
@DisplayName("POST /api/attendance/clock-in — メモ付きJSONボディで201を返す")
void clockIn_withMemoBody_returns201WithMemo() throws Exception {
    // Arrange
    var response = new AttendanceRecordResponse(
            UUID.randomUUID(),
            LocalDate.of(2025, 1, 15),
            Instant.parse("2025-01-15T00:00:00Z"),
            null,
            false,
            "直行"
    );
    when(attendanceService.clockIn(EMPLOYEE_ID, "直行")).thenReturn(response);

    // Act & Assert
    mockMvc.perform(post("/api/attendance/clock-in")
                    .param("employeeId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"直行\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").value("直行"));
}

@Test
@DisplayName("POST /api/attendance/clock-in — ボディなしでも201を返す（後方互換）")
void clockIn_withoutBody_returns201WithNullMemo() throws Exception {
    // Arrange
    var response = new AttendanceRecordResponse(
            UUID.randomUUID(),
            LocalDate.of(2025, 1, 15),
            Instant.parse("2025-01-15T00:00:00Z"),
            null,
            false,
            null
    );
    when(attendanceService.clockIn(EMPLOYEE_ID, null)).thenReturn(response);

    // Act & Assert
    mockMvc.perform(post("/api/attendance/clock-in")
                    .param("employeeId", EMPLOYEE_ID.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").doesNotExist());
}

@Test
@DisplayName("POST /api/attendance/clock-in — 201文字のメモで400バリデーションエラー")
void clockIn_memoExceeds200Chars_returns400() throws Exception {
    // Arrange
    var longMemo = "あ".repeat(201);

    // Act & Assert
    mockMvc.perform(post("/api/attendance/clock-in")
                    .param("employeeId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"" + longMemo + "\"}"))
            .andExpect(status().isBadRequest());
}
```

### 2.2 退勤打刻 — メモ付きリクエスト

```java
@Test
@DisplayName("POST /api/attendance/clock-out — メモ付きJSONボディで200を返す")
void clockOut_withMemoBody_returns200WithMemo() throws Exception {
    // Arrange
    var response = new AttendanceRecordResponse(
            UUID.randomUUID(),
            LocalDate.of(2025, 1, 15),
            Instant.parse("2025-01-14T23:00:00Z"),
            Instant.parse("2025-01-15T08:00:00Z"),
            false,
            "定時退勤"
    );
    when(attendanceService.clockOut(EMPLOYEE_ID, "定時退勤")).thenReturn(response);

    // Act & Assert
    mockMvc.perform(post("/api/attendance/clock-out")
                    .param("employeeId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"定時退勤\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("定時退勤"));
}
```

### 2.3 メモ更新エンドポイント

```java
@Test
@DisplayName("PUT /api/attendance/{id}/memo — 正常に200を返す")
void updateMemo_validRequest_returns200() throws Exception {
    // Arrange
    var recordId = UUID.randomUUID();
    var response = new AttendanceRecordResponse(
            recordId,
            LocalDate.of(2025, 1, 15),
            Instant.parse("2025-01-15T00:00:00Z"),
            null,
            false,
            "更新メモ"
    );
    when(attendanceService.updateMemo(recordId, EMPLOYEE_ID, "更新メモ", 1L)).thenReturn(response);

    // Act & Assert
    mockMvc.perform(put("/api/attendance/" + recordId + "/memo")
                    .param("editorId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"更新メモ\",\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("更新メモ"));
}

@Test
@DisplayName("PUT /api/attendance/{id}/memo — 201文字のメモで400バリデーションエラー")
void updateMemo_memoExceeds200Chars_returns400() throws Exception {
    // Arrange
    var recordId = UUID.randomUUID();
    var longMemo = "あ".repeat(201);

    // Act & Assert
    mockMvc.perform(put("/api/attendance/" + recordId + "/memo")
                    .param("editorId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"" + longMemo + "\",\"version\":0}"))
            .andExpect(status().isBadRequest());
}

@Test
@DisplayName("PUT /api/attendance/{id}/memo — versionなしで400バリデーションエラー")
void updateMemo_missingVersion_returns400() throws Exception {
    // Arrange
    var recordId = UUID.randomUUID();

    // Act & Assert
    mockMvc.perform(put("/api/attendance/" + recordId + "/memo")
                    .param("editorId", EMPLOYEE_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"メモ\"}"))
            .andExpect(status().isBadRequest());
}
```

### 2.4 メモ編集履歴取得エンドポイント

```java
@Test
@DisplayName("GET /api/attendance/{id}/memo/history — 200で履歴リストを返す")
void getMemoEditHistory_validRequest_returns200() throws Exception {
    // Arrange
    var recordId = UUID.randomUUID();
    var historyResponse = new MemoEditHistoryResponse(
            UUID.randomUUID(),
            EMPLOYEE_ID,
            "田中太郎",
            "旧メモ",
            "新メモ",
            Instant.parse("2025-01-15T01:00:00Z")
    );
    when(attendanceService.getMemoEditHistory(recordId)).thenReturn(List.of(historyResponse));

    // Act & Assert
    mockMvc.perform(get("/api/attendance/" + recordId + "/memo/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].editorName").value("田中太郎"))
            .andExpect(jsonPath("$[0].oldMemo").value("旧メモ"))
            .andExpect(jsonPath("$[0].newMemo").value("新メモ"));
}
```

---

## 3. Backend — Repository テスト

**ファイル:** `packages/backend/src/test/java/com/example/attendance/attendance/repository/MemoEditHistoryRepositoryTest.java`（新規）

```java
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class MemoEditHistoryRepositoryTest {

    @Autowired
    private MemoEditHistoryRepository memoEditHistoryRepository;

    @Autowired
    private EntityManager em;

    private Employee employee;
    private AttendanceRecord record;

    @BeforeEach
    void setUp() {
        var department = Department.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .name("Engineering")
                .build();
        em.persist(department);

        employee = Employee.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .name("田中太郎")
                .email("tanaka@example.com")
                .password("hashed")
                .department(department)
                .role(Role.EMPLOYEE)
                .isManager(false)
                .hireDate(LocalDate.of(2024, 4, 1))
                .build();
        em.persist(employee);

        record = AttendanceRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .employee(employee)
                .workDate(LocalDate.of(2025, 1, 15))
                .clockIn(Instant.parse("2025-01-14T23:00:00Z"))
                .corrected(false)
                .memo("初期メモ")
                .build();
        em.persist(record);
        em.flush();
    }

    @Test
    @DisplayName("attendance_record_id で検索し、edited_at 降順で返される")
    void findByAttendanceRecordIdOrderByEditedAtDesc_multipleEntries_returnsDescending() {
        // Arrange
        var history1 = MemoEditHistory.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .attendanceRecord(record)
                .editor(employee)
                .oldMemo(null)
                .newMemo("初期メモ")
                .editedAt(Instant.parse("2025-01-15T01:00:00Z"))
                .build();
        var history2 = MemoEditHistory.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .attendanceRecord(record)
                .editor(employee)
                .oldMemo("初期メモ")
                .newMemo("更新メモ")
                .editedAt(Instant.parse("2025-01-15T02:00:00Z"))
                .build();
        em.persist(history1);
        em.persist(history2);
        em.flush();

        // Act
        var results = memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(record.getId());

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEditedAt()).isAfter(results.get(1).getEditedAt());
        assertThat(results.get(0).getNewMemo()).isEqualTo("更新メモ");
    }

    @Test
    @DisplayName("該当レコードがない場合は空リストを返す")
    void findByAttendanceRecordIdOrderByEditedAtDesc_noEntries_returnsEmpty() {
        // Act
        var results = memoEditHistoryRepository.findByAttendanceRecordIdOrderByEditedAtDesc(UUID.randomUUID());

        // Assert
        assertThat(results).isEmpty();
    }
}
```

---

## 4. Frontend — MemoInput テスト

**ファイル:** `packages/frontend/src/features/attendance/MemoInput.test.tsx`（新規）

```typescript
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoInput } from "./MemoInput";

describe("MemoInput", () => {
  const mockOnChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("テキストエリアが表示される", () => {
    render(<MemoInput value="" onChange={mockOnChange} />);

    const textarea = screen.getByPlaceholderText("メモ（任意・200文字以内）");
    expect(textarea).toBeInTheDocument();
  });

  it("入力時にonChangeが呼ばれる", async () => {
    const user = userEvent.setup();
    render(<MemoInput value="" onChange={mockOnChange} />);

    const textarea = screen.getByPlaceholderText("メモ（任意・200文字以内）");
    await user.type(textarea, "在宅勤務");

    expect(mockOnChange).toHaveBeenCalled();
  });

  it("文字数カウンターが表示される", () => {
    render(<MemoInput value="テスト" onChange={mockOnChange} />);

    expect(screen.getByText("3/200")).toBeInTheDocument();
  });

  it("空の場合は0/200と表示される", () => {
    render(<MemoInput value="" onChange={mockOnChange} />);

    expect(screen.getByText("0/200")).toBeInTheDocument();
  });

  it("disabled時にテキストエリアが無効化される", () => {
    render(<MemoInput value="" onChange={mockOnChange} disabled />);

    const textarea = screen.getByPlaceholderText("メモ（任意・200文字以内）");
    expect(textarea).toBeDisabled();
  });

  it("maxLengthが200に設定されている", () => {
    render(<MemoInput value="" onChange={mockOnChange} />);

    const textarea = screen.getByPlaceholderText("メモ（任意・200文字以内）");
    expect(textarea).toHaveAttribute("maxLength", "200");
  });
});
```

---

## 5. Frontend — ClockButtons テスト（既存に追加）

**ファイル:** `packages/frontend/src/features/attendance/ClockButtons.test.tsx`（追加分）

```typescript
describe("メモ入力", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupMocks({ status: "NOT_CLOCKED_IN" });
  });

  it("メモ入力用テキストエリアが表示される", () => {
    const { container } = render(<ClockButtons />);

    const textarea = container.querySelector("textarea");
    expect(textarea).toBeInTheDocument();
  });

  it("出勤ボタンクリックでメモが mutate に渡される", async () => {
    const user = userEvent.setup();
    const { container } = render(<ClockButtons />);

    const textarea = container.querySelector("textarea")!;
    await user.type(textarea, "直行");

    const clockInButton = getButtonByText(container, "出勤")!;
    await user.click(clockInButton);

    expect(mockClockInMutate).toHaveBeenCalledWith("直行");
  });

  it("メモが空の場合はundefinedでmutateが呼ばれる", async () => {
    const user = userEvent.setup();
    const { container } = render(<ClockButtons />);

    const clockInButton = getButtonByText(container, "出勤")!;
    await user.click(clockInButton);

    expect(mockClockInMutate).toHaveBeenCalledWith(undefined);
  });

  it("退勤ボタンクリックでメモが mutate に渡される", async () => {
    vi.clearAllMocks();
    setupMocks({ status: "CLOCKED_IN" });
    const user = userEvent.setup();
    const { container } = render(<ClockButtons />);

    const textarea = container.querySelector("textarea")!;
    await user.type(textarea, "定時退勤");

    const clockOutButton = getButtonByText(container, "退勤")!;
    await user.click(clockOutButton);

    expect(mockClockOutMutate).toHaveBeenCalledWith("定時退勤");
  });
});
```

---

## 6. Frontend — MemoEditDialog テスト

**ファイル:** `packages/frontend/src/features/attendance/MemoEditDialog.test.tsx`（新規）

```typescript
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { type Mock, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoEditDialog } from "./MemoEditDialog";
import { useUpdateMemo } from "./useAttendance";

vi.mock("./useAttendance", () => ({
  useUpdateMemo: vi.fn(),
}));

vi.mock("@/components/FormDialog", () => ({
  FormDialog: (props: Record<string, unknown>) => {
    if (!props.open) return null;
    return createElement("div", { "data-testid": "form-dialog" },
      createElement("h2", null, props.title as string),
      props.children,
      createElement("button", {
        type: "button",
        onClick: props.onSubmit,
        "data-testid": "submit-button",
      }, props.submitLabel as string),
      createElement("button", {
        type: "button",
        onClick: () => (props.onOpenChange as (v: boolean) => void)(false),
        "data-testid": "cancel-button",
      }, "キャンセル"),
    );
  },
}));

const mockMutate = vi.fn();

function setupMock(overrides: { isPending?: boolean } = {}) {
  (useUpdateMemo as Mock).mockReturnValue({
    mutate: mockMutate,
    isPending: overrides.isPending ?? false,
  });
}

describe("MemoEditDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupMock();
  });

  it("openがfalseの場合は何も表示されない", () => {
    const { container } = render(
      <MemoEditDialog
        open={false}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo="テスト"
        version={0}
      />
    );

    expect(container.innerHTML).toBe("");
  });

  it("開いたときに現在のメモがプリフィルされる", () => {
    render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo="既存メモ"
        version={0}
      />
    );

    const textarea = screen.getByDisplayValue("既存メモ");
    expect(textarea).toBeInTheDocument();
  });

  it("currentMemoがnullの場合は空文字がプリフィルされる", () => {
    render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo={null}
        version={0}
      />
    );

    const textarea = screen.getByPlaceholderText("メモ（任意・200文字以内）");
    expect(textarea).toHaveValue("");
  });

  it("保存ボタンクリックでmutateが正しい引数で呼ばれる", async () => {
    const user = userEvent.setup();
    render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo="旧メモ"
        version={2}
      />
    );

    const textarea = screen.getByDisplayValue("旧メモ");
    await user.clear(textarea);
    await user.type(textarea, "新メモ");

    const submitButton = screen.getByTestId("submit-button");
    await user.click(submitButton);

    expect(mockMutate).toHaveBeenCalledWith(
      { recordId: "record-1", memo: "新メモ", version: 2 },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("キャンセルボタンクリックでonOpenChange(false)が呼ばれる", async () => {
    const user = userEvent.setup();
    const mockOnOpenChange = vi.fn();
    render(
      <MemoEditDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        recordId="record-1"
        currentMemo="テスト"
        version={0}
      />
    );

    const cancelButton = screen.getByTestId("cancel-button");
    await user.click(cancelButton);

    expect(mockOnOpenChange).toHaveBeenCalledWith(false);
  });

  it("タイトルが「メモ編集」と表示される", () => {
    render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo=""
        version={0}
      />
    );

    expect(screen.getByText("メモ編集")).toBeInTheDocument();
  });
});
```

---

## 7. Frontend — AttendanceTable テスト（既存に追加 or 新規）

**ファイル:** `packages/frontend/src/features/attendance/AttendanceTable.test.tsx`（新規）

```typescript
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { DailyAttendanceResponse } from "./attendance-api";
import { AttendanceTable } from "./AttendanceTable";

const baseDays: DailyAttendanceResponse[] = [
  {
    date: "2025-01-15",
    records: [
      {
        id: "rec-1",
        workDate: "2025-01-15",
        clockIn: "2025-01-14T23:00:00Z",
        clockOut: "2025-01-15T08:00:00Z",
        corrected: false,
        memo: "在宅勤務",
      },
    ],
    totalWorkMinutes: 540,
    breakMinutes: 60,
    workMinutes: 480,
    overtimeMinutes: 0,
  },
  {
    date: "2025-01-16",
    records: [
      {
        id: "rec-2",
        workDate: "2025-01-16",
        clockIn: "2025-01-15T23:00:00Z",
        clockOut: "2025-01-16T08:00:00Z",
        corrected: false,
        memo: null,
      },
    ],
    totalWorkMinutes: 540,
    breakMinutes: 60,
    workMinutes: 480,
    overtimeMinutes: 0,
  },
];

describe("AttendanceTable — メモ列", () => {
  it("メモ列のヘッダーが表示される", () => {
    render(<AttendanceTable days={baseDays} />);

    expect(screen.getByText("メモ")).toBeInTheDocument();
  });

  it("メモがあるレコードの場合はメモテキストが表示される", () => {
    render(<AttendanceTable days={baseDays} />);

    expect(screen.getByText("在宅勤務")).toBeInTheDocument();
  });

  it("メモがnullのレコードの場合はメモテキストが表示されない", () => {
    render(<AttendanceTable days={[baseDays[1]] />);

    expect(screen.queryByText("在宅勤務")).not.toBeInTheDocument();
  });
});
```

---

## 8. Frontend — TodayRecords テスト（既存に追加 or 新規）

**ファイル:** `packages/frontend/src/features/attendance/TodayRecords.test.tsx`（新規）

```typescript
import { render, screen } from "@testing-library/react";
import { createElement } from "react";
import { type Mock, beforeEach, describe, expect, it, vi } from "vitest";
import { TodayRecords } from "./TodayRecords";
import { useTodayStatus } from "./useAttendance";

vi.mock("./useAttendance", () => ({
  useTodayStatus: vi.fn(),
}));

vi.mock("@/components/ui/badge", () => ({
  Badge: (props: Record<string, unknown>) =>
    createElement("span", { "data-testid": "badge" }, props.children as string),
}));

vi.mock("@/components/ui/skeleton", () => ({
  Skeleton: (props: Record<string, unknown>) =>
    createElement("div", { "data-testid": "skeleton", ...props }),
}));

describe("TodayRecords — メモ表示", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("メモがあるレコードの場合はメモテキストが表示される", () => {
    (useTodayStatus as Mock).mockReturnValue({
      data: {
        status: "CLOCKED_IN",
        records: [
          {
            id: "rec-1",
            workDate: "2025-01-15",
            clockIn: "2025-01-14T23:00:00Z",
            clockOut: null,
            corrected: false,
            memo: "客先訪問",
          },
        ],
      },
      isLoading: false,
    });

    render(<TodayRecords />);

    expect(screen.getByText("客先訪問")).toBeInTheDocument();
  });

  it("メモがnullのレコードの場合はメモが表示されない", () => {
    (useTodayStatus as Mock).mockReturnValue({
      data: {
        status: "CLOCKED_IN",
        records: [
          {
            id: "rec-1",
            workDate: "2025-01-15",
            clockIn: "2025-01-14T23:00:00Z",
            clockOut: null,
            corrected: false,
            memo: null,
          },
        ],
      },
      isLoading: false,
    });

    render(<TodayRecords />);

    expect(screen.queryByText("客先訪問")).not.toBeInTheDocument();
  });

  it("メモ編集ボタンが表示される", () => {
    (useTodayStatus as Mock).mockReturnValue({
      data: {
        status: "CLOCKED_OUT",
        records: [
          {
            id: "rec-1",
            workDate: "2025-01-15",
            clockIn: "2025-01-14T23:00:00Z",
            clockOut: "2025-01-15T08:00:00Z",
            corrected: false,
            memo: "在宅",
          },
        ],
      },
      isLoading: false,
    });

    const { container } = render(<TodayRecords />);

    // Pencil icon button for editing memo
    const editButtons = container.querySelectorAll("button");
    const hasEditButton = Array.from(editButtons).some(
      (btn) => btn.getAttribute("aria-label") === "メモ編集"
    );
    expect(hasEditButton).toBe(true);
  });
});
```

---

## テスト実行コマンド

```bash
# Backend — 全テスト（Red 状態で失敗を確認）
cd packages/backend && ./mvnw test

# Frontend — 全テスト（Red 状態で失敗を確認）
cd packages/frontend && npm test

# 特定テストだけ実行
cd packages/backend && ./mvnw test -Dtest="AttendanceServiceTest"
cd packages/frontend && npx vitest run src/features/attendance/MemoInput.test.tsx
```

---

## Red → Green のチェックリスト

実装後に各テストが Green になったことを確認:

- [ ] `AttendanceServiceTest` — ClockInWithMemo (3 tests)
- [ ] `AttendanceServiceTest` — ClockOutWithMemo (2 tests)
- [ ] `AttendanceServiceTest` — UpdateMemo (4 tests)
- [ ] `AttendanceServiceTest` — GetMemoEditHistory (2 tests)
- [ ] `AttendanceControllerTest` — clock-in memo (3 tests)
- [ ] `AttendanceControllerTest` — clock-out memo (1 test)
- [ ] `AttendanceControllerTest` — updateMemo (3 tests)
- [ ] `AttendanceControllerTest` — getMemoEditHistory (1 test)
- [ ] `MemoEditHistoryRepositoryTest` (2 tests)
- [ ] `MemoInput.test.tsx` (6 tests)
- [ ] `ClockButtons.test.tsx` — メモ入力 (4 tests)
- [ ] `MemoEditDialog.test.tsx` (6 tests)
- [ ] `AttendanceTable.test.tsx` — メモ列 (3 tests)
- [ ] `TodayRecords.test.tsx` — メモ表示 (3 tests)

**合計: 43 テストケース**
