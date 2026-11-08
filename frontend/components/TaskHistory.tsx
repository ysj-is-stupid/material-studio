"use client";

import TaskCard from "./TaskCard";
import { useIdentity, useTasks } from "@/lib/tasks";

export default function TaskHistory({ compact = false }: { compact?: boolean }) {
  const identity = useIdentity();
  const tasks = useTasks(identity.data?.viewerId);

  if (identity.isError) return <p role="alert" className="state-error">{identity.error.message} <button onClick={() => identity.refetch()}>重试</button></p>;
  if (identity.isPending || tasks.isPending) return <p className="state-note">正在读取我的制作记录…</p>;
  if (tasks.isError && !tasks.data) return <p role="alert" className="state-error">{tasks.error.message} <button onClick={() => tasks.refetch()}>重试</button></p>;

  const items = compact ? tasks.data?.items.slice(0, 4) : tasks.data?.items;
  return (
    <>
      {tasks.isError && <p role="alert" className="state-error">状态暂时无法更新，已保留上次结果。<button onClick={() => tasks.refetch()}>重新查询</button></p>}
      {!items?.length ? <p className="state-note">还没有制作记录。选择模板后上传图片即可开始。</p> : (
        <div className="task-grid">{items.map((task) => <TaskCard key={task.id} task={task} />)}</div>
      )}
    </>
  );
}
