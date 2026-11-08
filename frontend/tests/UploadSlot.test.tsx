import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import UploadSlot from "@/components/UploadSlot";
import { uploadAsset, type Template } from "@/lib/api";

vi.mock("@/lib/api", () => ({ uploadAsset: vi.fn() }));

const template: Template = {
  id: "travel-postcard", title: "旅行明信片", category: "旅行纪念", description: "",
  coverUrl: "", requiredImageCount: 2, acceptedMimeTypes: ["image/png", "image/jpeg"],
  maxFileSizeBytes: 5_242_880,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => "blob:preview");
  URL.revokeObjectURL = vi.fn();
});
afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe("upload slot", () => {
  it("ignores a late replaced upload, then removes the ready image", async () => {
    const first = deferred<{ assetId: string }>();
    const second = deferred<{ assetId: string }>();
    vi.mocked(uploadAsset).mockReturnValueOnce(first.promise as ReturnType<typeof uploadAsset>)
      .mockReturnValueOnce(second.promise as ReturnType<typeof uploadAsset>);
    const onReady = vi.fn();
    render(<UploadSlot template={template} index={0} onReady={onReady} />);
    const input = screen.getByLabelText("上传第 1 张图片") as HTMLInputElement;

    fireEvent.change(input, { target: { files: [new File(["one"], "one.png", { type: "image/png" })] } });
    expect(screen.getByText("上传中…")).toBeTruthy();
    const oldSignal = vi.mocked(uploadAsset).mock.calls[0][2]!;
    fireEvent.change(input, { target: { files: [new File(["two"], "two.png", { type: "image/png" })] } });
    expect(oldSignal.aborted).toBe(true);
    await act(async () => { first.resolve({ assetId: "old" }); await first.promise; });
    expect(screen.getByText("上传中…")).toBeTruthy();
    expect(onReady).not.toHaveBeenCalledWith(0, "old");

    await act(async () => { second.resolve({ assetId: "new" }); await second.promise; });
    expect(screen.getByText("已准备好")).toBeTruthy();
    expect(onReady).toHaveBeenCalledWith(0, "new");
    fireEvent.click(screen.getByRole("button", { name: "移除" }));
    expect(screen.queryByText("已准备好")).toBeNull();
    expect(onReady).toHaveBeenLastCalledWith(0, null);
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });

  it("rejects an unsupported file before sending it and reports upload failure", async () => {
    const onReady = vi.fn();
    vi.mocked(uploadAsset).mockRejectedValue(new Error("服务暂时不可用"));
    render(<UploadSlot template={template} index={1} onReady={onReady} />);
    const input = screen.getByLabelText("上传第 2 张图片") as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(["x"], "bad.txt", { type: "text/plain" })] } });
    expect(screen.getByRole("alert").textContent).toContain("JPEG 或 PNG");
    expect(uploadAsset).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { files: [new File(["x"], "good.png", { type: "image/png" })] } });
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("服务暂时不可用"));
    expect(onReady).toHaveBeenLastCalledWith(1, null);
  });
});
