import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoInput } from "./MemoInput";

function getTextarea(container: HTMLElement) {
  return container.querySelector("textarea")!;
}

describe("MemoInput", () => {
  const mockOnChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("テキストエリアが表示される", () => {
    const { container } = render(<MemoInput value="" onChange={mockOnChange} />);

    expect(getTextarea(container)).toBeInTheDocument();
  });

  it("入力時にonChangeが呼ばれる", async () => {
    const user = userEvent.setup();
    const { container } = render(<MemoInput value="" onChange={mockOnChange} />);

    await user.type(getTextarea(container), "在宅勤務");

    expect(mockOnChange).toHaveBeenCalled();
  });

  it("文字数カウンターが表示される", () => {
    const { container } = render(<MemoInput value="テスト" onChange={mockOnChange} />);

    expect(container.textContent).toContain("3/200");
  });

  it("空の場合は0/200と表示される", () => {
    const { container } = render(<MemoInput value="" onChange={mockOnChange} />);

    expect(container.textContent).toContain("0/200");
  });

  it("disabled時にテキストエリアが無効化される", () => {
    const { container } = render(<MemoInput value="" onChange={mockOnChange} disabled />);

    expect(getTextarea(container)).toBeDisabled();
  });

  it("maxLengthが200に設定されている", () => {
    const { container } = render(<MemoInput value="" onChange={mockOnChange} />);

    expect(getTextarea(container)).toHaveAttribute("maxLength", "200");
  });
});
