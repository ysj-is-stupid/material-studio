import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import Header from "@/components/Header";
import { getIdentity, switchIdentity } from "@/lib/api";

vi.mock("@/lib/api", () => ({ getIdentity: vi.fn(), switchIdentity: vi.fn() }));

afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe("demo identity controls", () => {
  it("shows the current identity and reports a failed switch without changing it", async () => {
    vi.mocked(getIdentity).mockResolvedValue({ viewerId: "a1b2c3d4-identity", slot: "A" });
    vi.mocked(switchIdentity).mockRejectedValue(new Error("服务暂时不可用"));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><Header /></QueryClientProvider>);
    fireEvent.click(await screen.findByRole("button", { name: "演示账户 A" }));
    expect(screen.getByText("身份编号：a1b2c3d4")).toBeTruthy();
    expect(screen.getByRole("button", { name: "用户 A（当前）" }).hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "用户 B" }));
    await waitFor(() => expect(switchIdentity).toHaveBeenCalledWith("B"));
    expect(await screen.findByRole("alert")).toHaveProperty("textContent", "服务暂时不可用");
    expect(screen.getByRole("button", { name: "演示账户 A" })).toBeTruthy();
  });
});
