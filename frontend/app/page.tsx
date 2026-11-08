"use client";

import Header from "@/components/Header";
import { getTemplates } from "@/lib/api";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

export default function Home() {
  const [category, setCategory] = useState("全部");
  const templates = useQuery({ queryKey: ["templates"], queryFn: ({ signal }) => getTemplates(signal) });
  const items = templates.data?.items.filter((item) => category === "全部" || item.category === category) ?? [];

  return (
    <>
      <Header />
      <main className="page">
        <section className="hero">
          <p className="eyebrow">MATERIAL STUDIO</p>
          <h1>选择一个灵感，制作你的图片</h1>
          <p>上传自己的照片，体验后台分步制作；离开页面后仍可在“我的记录”查看进度。</p>
        </section>
        <div className="section-heading"><h2>发现模板</h2><span>从喜欢的主题开始</span></div>
        {templates.isPending && <p className="state-note">正在加载模板…</p>}
        {templates.isError && <p role="alert" className="state-error">{templates.error.message} <button onClick={() => templates.refetch()}>重试</button></p>}
        {templates.data && (
          <>
            <div className="category-bar" role="group" aria-label="模板分类">
              {["全部", ...templates.data.categories].map((name) => (
                <button key={name} type="button" className={category === name ? "active" : ""}
                  aria-pressed={category === name} onClick={() => setCategory(name)}>{name}</button>
              ))}
            </div>
            {items.length === 0 ? <p className="state-note">这个分类暂时没有模板。</p> : (
              <div className="template-grid">
                {items.map((item) => (
                  <Link className="template-card" href={`/templates/${item.id}`} key={item.id}>
                    {/* API cover images are local SVG assets served by Java. */}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={item.coverUrl} alt={`${item.title}示例封面`} />
                    <div className="template-card-body"><span>{item.category}</span><h3>{item.title}</h3><p>{item.description}</p></div>
                  </Link>
                ))}
              </div>
            )}
          </>
        )}
      </main>
    </>
  );
}
