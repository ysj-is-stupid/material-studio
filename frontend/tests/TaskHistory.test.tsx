import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TaskHistory from "@/components/TaskHistory";
import { getIdentity, getTasks, type Task, type TaskPage } from "@/lib/api";

vi.mock("@/lib/api", () => ({ getIdentity: vi.fn(), getTasks: vi.fn() }));

const identity = vi.mocked(getIdentity);
const tasks = vi.mocked(getTasks);

function task(id: string, status: Task["status"], reason: string | null = null): Task {
  return {
    id, templateId: "travel-postcard", templateTitle: `任务${id}`,
    status, currentStage: status === "RUNNING" ? "ASSET_CHECK" : "GENERATE",
    failureReason: reason, resultUrl: null,
    createdAt: "2026-11-08T08:00:00", startedAt: null, finishedAt: null, stageHistory: [],
  };
}

function page(...items: Task[]): TaskPage { return { items, nextCursor: null }; }

function renderHistory() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><TaskHistory /></QueryClientProvider>);
  return client;
}

beforeEach(() => {
  identity.mockResolvedValue({ viewerId: "viewer-a", slot: "A" });
});
afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe("personal task history", () => {
  it("keeps two task cards separate and preserves them when status queries fail", async () => {
    tasks.mockResolvedValueOnce(page(task("甲", "RUNNING"), task("乙", "RUNNING")))
      .mockResolvedValueOnce(page(task("甲", "FAILED", "模拟失败：模拟生成"), task("乙", "RUNNING")))
      .mockRejectedValueOnce(new Error("网络中断"));
    const client = renderHistory();
    await screen.findByText("任务甲");

    await act(async () => { await client.refetchQueries({ queryKey: ["tasks", "viewer-a"] }); });
    await screen.findByText("模拟失败：模拟生成");
    const first = screen.getByText("任务甲").closest("article")!;
    const second = screen.getByText("任务乙").closest("article")!;
    expect(within(first).getByText("制作失败")).toBeTruthy();
    expect(within(second).getByText("制作中")).toBeTruthy();
    expect(within(second).queryByText("模拟失败：模拟生成")).toBeNull();

    await act(async () => { await client.refetchQueries({ queryKey: ["tasks", "viewer-a"] }); });
    await screen.findByText(/状态暂时无法更新/);
    expect(screen.getByText("任务甲")).toBeTruthy();
    expect(screen.getByText("任务乙")).toBeTruthy();
    expect(within(first).getByText("制作失败")).toBeTruthy();
    expect(within(second).getByText("制作中")).toBeTruthy();
  });

  it("stops polling after every task is terminal", async () => {
    tasks.mockResolvedValue(page(task("甲", "FAILED", "模拟失败：结果整理")));
    renderHistory();
    await screen.findByText("任务甲");
    await new Promise((resolve) => setTimeout(resolve, 1750));
    expect(tasks).toHaveBeenCalledTimes(1);
  }, 5000);

  it("continues tracking an unfinished task", async () => {
    tasks.mockResolvedValue(page(task("乙", "RUNNING")));
    renderHistory();
    await screen.findByText("任务乙");
    await waitFor(() => expect(tasks.mock.calls.length).toBeGreaterThanOrEqual(2), { timeout: 3500 });
  }, 5000);

  it("polls a mixed list until its last active task finishes, then stops", async () => {
    tasks.mockResolvedValueOnce(page(task("甲", "SUCCEEDED"), task("乙", "RUNNING")))
      .mockResolvedValue(page(task("甲", "SUCCEEDED"), task("乙", "SUCCEEDED")));
    renderHistory();
    await screen.findByText("任务乙");
    await waitFor(() => expect(tasks).toHaveBeenCalledTimes(2), { timeout: 3500 });
    await screen.findAllByText("制作成功");
    await new Promise((resolve) => setTimeout(resolve, 1750));
    expect(tasks).toHaveBeenCalledTimes(2);
  }, 6000);

  it("aborts the status request when the history view is left", async () => {
    const aborted = vi.fn();
    tasks.mockImplementation((signal) => new Promise<TaskPage>(() => {
      signal?.addEventListener("abort", aborted);
    }));
    renderHistory();
    await waitFor(() => expect(tasks).toHaveBeenCalledTimes(1));
    cleanup();
    expect(aborted).toHaveBeenCalledTimes(1);
  });

  it("does not show a late response from the previous identity", async () => {
    const previous = deferredPage();
    tasks.mockReturnValueOnce(previous.promise).mockResolvedValue(page(task("乙", "SUCCEEDED")));
    const client = renderHistory();
    await waitFor(() => expect(tasks).toHaveBeenCalledTimes(1));
    act(() => { client.setQueryData(["identity"], { viewerId: "viewer-b", slot: "B" }); });
    await screen.findByText("任务乙");
    await act(async () => { previous.resolve(page(task("甲", "SUCCEEDED"))); await previous.promise; });
    expect(screen.queryByText("任务甲")).toBeNull();
  });
});

function deferredPage() {
  let resolve!: (value: TaskPage) => void;
  const promise = new Promise<TaskPage>((done) => { resolve = done; });
  return { promise, resolve };
}
