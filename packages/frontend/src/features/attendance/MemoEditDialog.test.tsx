import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { beforeEach, describe, expect, it, type Mock, vi } from "vitest";
import { MemoEditDialog } from "./MemoEditDialog";
import { useUpdateMemo } from "./useAttendance";

vi.mock("./useAttendance", () => ({
  useUpdateMemo: vi.fn(),
}));

vi.mock("@/components/FormDialog", () => ({
  FormDialog: (props: Record<string, unknown>) => {
    if (!props.open) return null;
    return createElement(
      "div",
      { "data-testid": "form-dialog" },
      createElement("h2", null, props.title as string),
      props.children,
      createElement(
        "button",
        {
          type: "button",
          onClick: props.onSubmit,
          "data-testid": "submit-button",
        },
        props.submitLabel as string,
      ),
      createElement(
        "button",
        {
          type: "button",
          onClick: () => (props.onOpenChange as (v: boolean) => void)(false),
          "data-testid": "cancel-button",
        },
        "キャンセル",
      ),
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
      />,
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
      />,
    );

    const textarea = screen.getByDisplayValue("既存メモ");
    expect(textarea).toBeInTheDocument();
  });

  it("currentMemoがnullの場合は空文字がプリフィルされる", () => {
    const { container } = render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo={null}
        version={0}
      />,
    );

    const textarea = container.querySelector("textarea")!;
    expect(textarea).toHaveValue("");
  });

  it("保存ボタンクリックでmutateが正しい引数で呼ばれる", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo="旧メモ"
        version={2}
      />,
    );

    const textarea = container.querySelector("textarea")!;
    await user.clear(textarea);
    await user.type(textarea, "新メモ");

    const submitButton = container.querySelector("[data-testid='submit-button']")!;
    await user.click(submitButton);

    expect(mockMutate).toHaveBeenCalledWith(
      { recordId: "record-1", memo: "新メモ", version: 2 },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });

  it("キャンセルボタンクリックでonOpenChange(false)が呼ばれる", async () => {
    const user = userEvent.setup();
    const mockOnOpenChange = vi.fn();
    const { container } = render(
      <MemoEditDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        recordId="record-1"
        currentMemo="テスト"
        version={0}
      />,
    );

    const cancelButton = container.querySelector("[data-testid='cancel-button']")!;
    await user.click(cancelButton);

    expect(mockOnOpenChange).toHaveBeenCalledWith(false);
  });

  it("タイトルが「メモ編集」と表示される", () => {
    const { container } = render(
      <MemoEditDialog
        open={true}
        onOpenChange={vi.fn()}
        recordId="record-1"
        currentMemo=""
        version={0}
      />,
    );

    expect(container.textContent).toContain("メモ編集");
  });
});
