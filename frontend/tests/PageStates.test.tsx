import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, it, vi } from "vitest";
import Home from "@/app/page";
import TemplatePage from "@/app/templates/[id]/page";
import { getIdentity, getTemplate, getTemplates } from "@/lib/api";

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "missing" }) }));
vi.mock("@/lib/api", () => ({
  getIdentity: vi.fn(), getTemplate: vi.fn(), getTemplates: vi.fn(), switchIdentity: vi.fn(),
}));

afterEach(() => { cleanup(); vi.clearAllMocks(); });

function renderPage(page: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{page}</QueryClientProvider>);
}

it("explains a list request failure and shows the empty state after retry", async () => {
  vi.mocked(getIdentity).mockResolvedValue({ viewerId: "viewer-a", slot: "A" });
  vi.mocked(getTemplates).mockRejectedValueOnce(new Error("服务暂时不可用"))
    .mockResolvedValue({ categories: [], items: [] });
  renderPage(<Home />);
  expect((await screen.findByRole("alert")).textContent).toContain("服务暂时不可用");
  fireEvent.click(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText("这个分类暂时没有模板。")).toBeTruthy();
});

it("explains an unavailable template instead of showing upload controls", async () => {
  vi.mocked(getIdentity).mockResolvedValue({ viewerId: "viewer-a", slot: "A" });
  vi.mocked(getTemplate).mockRejectedValue(new Error("模板不存在"));
  renderPage(<TemplatePage />);
  expect((await screen.findByRole("alert")).textContent).toContain("模板不存在");
  expect(screen.queryByRole("button", { name: "开始制作" })).toBeNull();
});
