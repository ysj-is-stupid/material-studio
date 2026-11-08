import Header from "@/components/Header";
import TaskHistory from "@/components/TaskHistory";

export default function HistoryPage() {
  return (
    <>
      <Header />
      <main className="page">
        <section className="page-intro"><p className="eyebrow">MY CREATIONS</p><h1>我的制作记录</h1>
          <p>等待中和制作中的任务会自动更新。刷新页面后仍可从后台读取记录。</p></section>
        <TaskHistory />
      </main>
    </>
  );
}
