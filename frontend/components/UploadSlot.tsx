"use client";

import type { Template } from "@/lib/api";
import { uploadAsset } from "@/lib/api";
import { useEffect, useRef, useState } from "react";

type UploadState = "EMPTY" | "UPLOADING" | "READY" | "FAILED";

export default function UploadSlot({ template, index, onReady }: {
  template: Template;
  index: number;
  onReady: (index: number, assetId: string | null) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const controller = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const previewRef = useRef<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [fileName, setFileName] = useState("");
  const [state, setState] = useState<UploadState>("EMPTY");
  const [error, setError] = useState("");

  useEffect(() => () => {
    controller.current?.abort();
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
  }, []);

  function reset() {
    sequence.current += 1;
    controller.current?.abort();
    controller.current = null;
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
    previewRef.current = null;
    setPreview(null);
    setFileName("");
    setError("");
    setState("EMPTY");
    onReady(index, null);
    if (input.current) input.current.value = "";
  }

  async function select(file?: File) {
    if (!file) return;
    reset();
    if (!template.acceptedMimeTypes.includes(file.type)) {
      setError("请选择 JPEG 或 PNG 图片");
      setState("FAILED");
      return;
    }
    if (file.size > template.maxFileSizeBytes) {
      setError(`图片不能超过 ${Math.round(template.maxFileSizeBytes / 1024 / 1024)} MB`);
      setState("FAILED");
      return;
    }
    const url = URL.createObjectURL(file);
    previewRef.current = url;
    setPreview(url);
    setFileName(file.name);
    setState("UPLOADING");
    const currentSequence = sequence.current;
    const abort = new AbortController();
    controller.current = abort;
    try {
      const uploaded = await uploadAsset(template.id, file, abort.signal);
      if (currentSequence !== sequence.current) return;
      setState("READY");
      onReady(index, uploaded.assetId);
    } catch (cause) {
      if (currentSequence !== sequence.current || abort.signal.aborted) return;
      setError(cause instanceof Error ? cause.message : "上传失败，请重新选择图片");
      setState("FAILED");
      onReady(index, null);
    }
  }

  return (
    <div className={`upload-slot upload-${state.toLowerCase()}`}>
      <input ref={input} type="file" tabIndex={-1} accept={template.acceptedMimeTypes.join(",")}
        aria-label={`上传第 ${index + 1} 张图片`}
        onChange={(event) => void select(event.target.files?.[0])} className="visually-hidden" />
      {preview ? (
        // Local previews use a temporary blob URL and are not processed by Next Image.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={preview} alt={`第 ${index + 1} 张待制作图片预览`} className="upload-preview" />
      ) : <div className="upload-empty-icon" aria-hidden="true">＋</div>}
      <div className="upload-meta">
        <strong>图片 {index + 1}</strong>
        <span>{state === "UPLOADING" ? "上传中…" : state === "READY" ? "已准备好" : fileName || "选择一张图片"}</span>
        {error && <span role="alert" className="upload-error">{error}</span>}
      </div>
      <div className="upload-actions">
        <button type="button" onClick={() => input.current?.click()}>{preview ? "替换" : "选择图片"}</button>
        {preview && <button type="button" onClick={reset}>移除</button>}
      </div>
    </div>
  );
}
