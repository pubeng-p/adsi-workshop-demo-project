import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  describe("NOT_CLOCKED_IN 状態", () => {
    it("出勤ボタンが有効である", () => {
      setupMocks({ status: "NOT_CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "出勤")).toBeEnabled();
    });

    it("退勤ボタンが無効化される", () => {
      setupMocks({ status: "NOT_CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "退勤")).toBeDisabled();
    });

    it("出勤ボタンクリックで mutate が呼ばれる", async () => {
      setupMocks({ status: "NOT_CLOCKED_IN" });
      const { container } = render(<ClockButtons />);
      const user = userEvent.setup();
      const btn = getButtonByText(container, "出勤");

      expect(btn).toBeDefined();
      await user.click(btn as HTMLElement);

      expect(mockClockInMutate).toHaveBeenCalledTimes(1);
    });
  });

  describe("CLOCKED_IN 状態", () => {
    it("出勤ボタンが無効化される", () => {
      setupMocks({ status: "CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "出勤")).toBeDisabled();
    });

    it("退勤ボタンが有効である", () => {
      setupMocks({ status: "CLOCKED_IN" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "退勤")).toBeEnabled();
    });

    it("退勤ボタンクリックで mutate が呼ばれる", async () => {
      setupMocks({ status: "CLOCKED_IN" });
      const { container } = render(<ClockButtons />);
      const user = userEvent.setup();
      const btn = getButtonByText(container, "退勤");

      expect(btn).toBeDefined();
      await user.click(btn as HTMLElement);

      expect(mockClockOutMutate).toHaveBeenCalledTimes(1);
    });
  });

  describe("CLOCKED_OUT 状態", () => {
    it("出勤ボタンが無効化される", () => {
      setupMocks({ status: "CLOCKED_OUT" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "出勤")).toBeDisabled();
    });

    it("退勤ボタンが無効化される", () => {
      setupMocks({ status: "CLOCKED_OUT" });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "退勤")).toBeDisabled();
    });
  });

  describe("ペンディング中", () => {
    it("mutation 実行中は両ボタンが無効化される", () => {
      setupMocks({ status: "NOT_CLOCKED_IN", clockInPending: true });
      const { container } = render(<ClockButtons />);

      expect(getButtonByText(container, "出勤")).toBeDisabled();
      expect(getButtonByText(container, "退勤")).toBeDisabled();
    });
  });

  describe("ローディング中", () => {
    it("スケルトンが表示される", () => {
      setupMocks({ isLoading: true });
      const { container } = render(<ClockButtons />);

      expect(container.querySelectorAll("[data-testid='skeleton']").length).toBeGreaterThan(0);
      expect(getButtonByText(container, "出勤")).toBeUndefined();
    });
  });
});
