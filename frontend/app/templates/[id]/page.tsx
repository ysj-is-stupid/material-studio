"use client";

import Header from "@/components/Header";
import TaskHistory from "@/components/TaskHistory";
import UploadSlot from "@/components/UploadSlot";
import { createTask, getTemplate } from "@/lib/api";
import { useIdentity } from "@/lib/tasks";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function TemplatePage() {
  const { id } = useParams<{ id: string }>();
  const template = useQuery({ queryKey: ["template", id], queryFn: ({ signal }) => getTemplate(id, signal), enabled: Boolean(id) });
  const identity = useIdentity();
  const queryClient = useQueryClient();
  const [assetIds, setAssetIds] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  const submit = useMutation({
    mutationFn: () => createTask(id, assetIds),
    onSuccess: (result) => {
      setNotice(`任务已受理：${result.taskId.slice(0, 8)}。可以继续制作或离开页面。`);
      void queryClient.invalidateQueries({ queryKey: ["tasks", identity.data?.viewerId] });
    },
  });

  function onReady(index: number, assetId: string | null) {
    setAssetIds((current) => {
      const updated = [...current];
      updated[index] = assetId ?? "";
      return updated;
    });
    setNotice("");
  }

  return (
    <>
      <Header />
      <main className="page">
        <Link href="/" className="back-link">← 返回模板列表</Link>
        {template.isPending && <p className="state-note">正在加载模板详情…</p>}
        {template.isError && <p role="alert" className="state-error">{template.error.message} <button onClick={() => template.refetch()}>重试</button></p>}
        {template.data && (
          <div className="studio-layout">
            <section className="studio-main">
              <div className="studio-cover">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={template.data.coverUrl} alt={`${template.data.title}示例封面`} />
              </div>
              <p className="eyebrow">{template.data.category}</p>
              <h1>{template.data.title}</h1>
              <p className="studio-description">{template.data.description}</p>
              <div className="upload-heading"><h2>上传素材</h2>
                <span>需要 {template.data.requiredImageCount} 张，JPEG / PNG，每张不超过 {Math.round(template.data.maxFileSizeBytes / 1024 / 1024)} MB</span></div>
              <div className="upload-grid">
                {Array.from({ length: template.data.requiredImageCount }, (_, index) => (
                  <UploadSlot key={`${id}-${index}`} template={template.data!} index={index} onReady={onReady} />
                ))}
              </div>
              <button className="primary-button" type="button" onClick={() => submit.mutate()}
                disabled={submit.isPending || identity.isPending || identity.isError
                  || assetIds.slice(0, template.data.requiredImageCount).filter(Boolean).length !== template.data.requiredImageCount}>
                {submit.isPending ? "正在提交…" : "开始制作"}
              </button>
              {submit.isError && <p role="alert" className="state-error">{submit.error.message}</p>}
              {identity.isError && <p role="alert" className="state-error">身份暂不可用，请刷新页面后重试。</p>}
              {notice && <p role="status" className="state-success">{notice}</p>}
            </section>
            <aside className="studio-side"><div className="section-heading"><h2>最近任务</h2><Link href="/history">查看全部 →</Link></div>
              <TaskHistory compact /></aside>
          </div>
        )}
      </main>
    </>
  );
}
