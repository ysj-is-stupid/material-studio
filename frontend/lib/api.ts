export type Template = {
  id: string;
  title: string;
  category: string;
  description: string;
  coverUrl: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};

export type TemplateList = { categories: string[]; items: Template[] };
export type Identity = { viewerId: string; slot: "A" | "B" };
export type UploadedAsset = { assetId: string; previewUrl: string; mimeType: string; sizeBytes: number };
export type StageEvent = { stage: string; state: string; startedAt: string; finishedAt: string | null; failureReason: string | null };
export type Task = {
  id: string;
  templateId: string;
  templateTitle: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  currentStage: "NONE" | "ASSET_CHECK" | "GENERATE" | "FINALIZE";
  failureReason: string | null;
  resultUrl: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  stageHistory: StageEvent[];
};
export type TaskPage = { items: Task[]; nextCursor: string | null };

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.message ?? `请求失败 (${response.status})`);
  }
  return body as T;
}

export async function getIdentity(signal?: AbortSignal) {
  return readJson<Identity>(await fetch("/api/identity", { signal, credentials: "same-origin", cache: "no-store" }));
}

export async function switchIdentity(slot: "A" | "B") {
  return readJson<Identity>(await fetch("/api/identity/switch", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ slot }), credentials: "same-origin",
  }));
}

export async function getTemplates(signal?: AbortSignal) {
  return readJson<TemplateList>(await fetch("/api/templates", { signal, cache: "no-store" }));
}

export async function getTemplate(id: string, signal?: AbortSignal) {
  return readJson<Template>(await fetch(`/api/templates/${encodeURIComponent(id)}`, { signal, cache: "no-store" }));
}

export async function uploadAsset(templateId: string, file: File, signal?: AbortSignal) {
  const form = new FormData();
  form.set("templateId", templateId);
  form.set("file", file);
  return readJson<UploadedAsset>(await fetch("/api/assets", {
    method: "POST", body: form, signal, credentials: "same-origin",
  }));
}

export async function createTask(templateId: string, assetIds: string[]) {
  return readJson<{ taskId: string; status: string }>(await fetch("/api/tasks", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ templateId, assetIds }),
    credentials: "same-origin",
  }));
}

export async function getTasks(signal?: AbortSignal) {
  return readJson<TaskPage>(await fetch("/api/tasks", { signal, credentials: "same-origin", cache: "no-store" }));
}
