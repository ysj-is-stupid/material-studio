"use client";

import Link from "next/link";
import { useState } from "react";
import { useIdentity } from "@/lib/tasks";
import { switchIdentity } from "@/lib/api";

export default function Header() {
  const identity = useIdentity();
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState("");

  async function select(slot: "A" | "B") {
    if (switching || slot === identity.data?.slot) return;
    setSwitching(true);
    setError("");
    try {
      await switchIdentity(slot);
      // A full navigation clears uploads and in-memory task queries from the previous identity.
      window.location.assign("/history");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "切换失败，请重试");
      setSwitching(false);
    }
  }

  return (
    <header className="site-header">
      <Link href="/" className="brand"><span className="brand-mark">M</span> 素材工坊</Link>
      <nav aria-label="主导航">
        <Link href="/">发现模板</Link>
        <Link href="/history">我的记录</Link>
        <div className="account-menu">
          <button type="button" className="account-trigger" aria-expanded={open} aria-controls="account-panel"
            onClick={() => { setOpen(!open); setError(""); }}>
            演示账户{identity.data ? ` ${identity.data.slot}` : ""}
          </button>
          {open && <div id="account-panel" className="account-panel" role="group" aria-label="演示账户管理">
            <strong>用户身份与个人记录</strong>
            {identity.isPending && <p>正在建立演示身份…</p>}
            {identity.isError && <p role="alert">身份暂不可用。<button type="button" onClick={() => identity.refetch()}>重试</button></p>}
            {identity.data && <>
              <p>当前：演示用户 {identity.data.slot}<br /><small>身份编号：{identity.data.viewerId.slice(0, 8)}</small></p>
              <div className="account-choices" aria-label="切换演示用户">
                {(["A", "B"] as const).map((slot) => <button key={slot} type="button"
                  aria-pressed={identity.data?.slot === slot} disabled={switching || identity.data?.slot === slot}
                  onClick={() => void select(slot)}>
                  {switching ? "请稍候…" : `用户 ${slot}${identity.data?.slot === slot ? "（当前）" : ""}`}
                </button>)}
              </div>
              <p className="account-hint">两个账户的制作记录分别保存。切换后打开对应账户的“我的记录”。</p>
              <Link href="/history" onClick={() => setOpen(false)}>查看我的记录 →</Link>
            </>}
            {error && <p role="alert" className="account-error">{error}</p>}
          </div>}
        </div>
      </nav>
    </header>
  );
}
