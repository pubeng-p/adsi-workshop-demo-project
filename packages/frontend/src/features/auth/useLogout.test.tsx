import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useLogout } from "./useAuth";

const mockReplace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

vi.mock("./auth-api", () => ({
  logout: vi.fn(),
}));

import { logout as logoutApi } from "./auth-api";
const mockLogoutApi = logoutApi as ReturnType<typeof vi.fn>;

function mutateLogout() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  const { result } = renderHook(() => useLogout(), { wrapper: Wrapper });
  result.current.mutate();
  return { queryClient };
}

describe("useLogout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("ログアウト成功時に /login へ replace で遷移する", async () => {
    mockLogoutApi.mockResolvedValue(undefined);
    mutateLogout();

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
      expect(mockReplace).toHaveBeenCalledTimes(1);
    });
  });

  it("ログアウト失敗時にも /login へリダイレクトする", async () => {
    mockLogoutApi.mockRejectedValue(new Error("Network error"));
    mutateLogout();

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
    });
  });

  it("ログアウト後にクエリキャッシュがクリアされる", async () => {
    mockLogoutApi.mockResolvedValue(undefined);
    const { queryClient } = mutateLogout();
    queryClient.setQueryData(["auth", "me"], { id: "1", name: "Test" });

    await waitFor(() => {
      expect(queryClient.getQueryData(["auth", "me"])).toBeUndefined();
    });
  });
});
