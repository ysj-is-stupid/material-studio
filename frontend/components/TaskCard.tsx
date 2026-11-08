import type { Task } from "@/lib/api";

const statusText: Record<Task["status"], string> = {
  QUEUED: "等待处理", RUNNING: "制作中", SUCCEEDED: "制作成功", FAILED: "制作失败",
};
const stageText: Record<Task["currentStage"], string> = {
  NONE: "等待开始", ASSET_CHECK: "素材检查", GENERATE: "模拟生成", FINALIZE: "结果整理",
};

export default function TaskCard({ task }: { task: Task }) {
  const date = new Date(`${task.createdAt}Z`);
  return (
    <article className="task-card">
      <div className="task-topline">
        <span className={`status status-${task.status.toLowerCase()}`}>{statusText[task.status]}</span>
        <time dateTime={task.createdAt}>{Number.isNaN(date.valueOf()) ? task.createdAt : date.toLocaleString("zh-CN")}</time>
      </div>
      <h3>{task.templateTitle}</h3>
      <p className="task-stage">{task.status === "SUCCEEDED" ? "全部完成" : stageText[task.currentStage]}</p>
      {task.status === "RUNNING" && <div className="progress-track"><span /></div>}
      {task.status === "FAILED" && <p role="alert" className="task-error">{task.failureReason}</p>}
      {task.status === "SUCCEEDED" && task.resultUrl && (
        // Result URLs are owner-checked API responses, so the browser must send its cookie directly.
        // eslint-disable-next-line @next/next/no-img-element
        <img className="task-result" src={task.resultUrl} alt={`${task.templateTitle}制作结果`} />
      )}
      <p className="task-id">任务 {task.id.slice(0, 8)}</p>
    </article>
  );
}
