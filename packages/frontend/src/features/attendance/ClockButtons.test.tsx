import { render } from "@testing-library/react";
import { createElement } from "react";
import { type Mock, beforeEach, describe, expect, it, vi } from "vitest";
import { ClockButtons } from "./ClockButtons";
import { useClockIn, useClockOut, useTodayStatus } from "./useAttendance";

vi.mock("./useAttendance", () => ({
  useTodayStatus: vi.fn(),
  useClockIn: vi.fn(),
  useClockOut: vi.fn(),
}));

vi.mock("@/components/ui/skeleton", () => ({
  Skeleton: (props: Record<string, unknown>) =>
    createElement("div", { "data-testid": "skeleton", ...props }),
}));

const mockClockInMutate = vi.fn();
const mockClockOutMutate = vi.fn();

function setupMocks(overrides: {
  status?: "NOT_CLOCKED_IN" | "CLOCKED_IN" | "CLOCKED_OUT";
  isLoading?: boolean;
  clockInPending?: boolean;
  clockOutPending?: boolean;
} = {}) {
  const {
    status = "NOT_CLOCKED_IN",
    isLoading = false,
    clockInPending = false,
    clockOutPending = false,
  } = overrides;

  (useTodayStatus as Mock).mockReturnValue({
    data: isLoading ? undefined : { status, records: [] },
    isLoading,
  });

  (useClockIn as Mock).mockReturnValue({
    mutate: mockClockInMutate,
    isPending: clockInPending,
  });

  (useClockOut as Mock).mockReturnValue({
    mutate: mockClockOutMutate,
    isPending: clockOutPending,
  });
}

function getButtonByText(container: HTMLElement, text: string) {
  const buttons = container.querySelectorAll("button");
  return Array.from(buttons).find((btn) => btn.textContent?.includes(text));
}

describe("ClockButtons", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Issue #1: 出勤ボタンが連続クリック可能", () => {
    it("CLOCKED_IN 状態では出勤ボタンが無効化される", () => {
      setupMocks({ status: "CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "出勤")).toBeDisabled();
    });

    it("CLOCKED_IN 状態では退勤ボタンが有効である", () => {
      setupMocks({ status: "CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "退勤")).toBeEnabled();
    });
  });
});
