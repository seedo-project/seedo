"use client";

import { Plus, Search, Shuffle } from "lucide-react";
import { useState } from "react";

import { IdeaCard, type Idea } from "./idea-card";

export function IdeaFeed({ ideas }: { ideas: Idea[] }) {
  const [searchQuery, setSearchQuery] = useState("");

  const trimmed = searchQuery.trim().toLowerCase();
  const visible = !trimmed
    ? ideas
    : ideas.filter((i) => {
        if (i.variant === "default") {
          return i.tags.some((t) => t.toLowerCase().includes(trimmed));
        }
        return (
          i.title.toLowerCase().includes(trimmed) ||
          i.description.toLowerCase().includes(trimmed)
        );
      });

  return (
    <main className="px-[100px] pt-10 pb-24">
      <div className="flex items-center justify-center gap-3">
        <div className="flex h-12 w-[610px] items-center rounded-full border-2 border-primary bg-card px-5 shadow-md">
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="검색어를 입력하세요"
            aria-label="아이디어 검색"
            className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground/70 focus:outline-none"
          />
          <Search className="size-7 text-foreground" aria-hidden />
        </div>
        <button
          type="button"
          className="flex h-12 items-center gap-2 rounded-full bg-primary px-6 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground shadow-md hover:bg-primary/90"
        >
          <Shuffle className="size-6" aria-hidden />
          랜덤 아이디어 보기?
        </button>
      </div>

      <section className="mt-12 grid grid-cols-3 gap-5">
        {visible.length === 0 ? (
          <div className="col-span-3 flex h-40 items-center justify-center rounded-xl border border-border text-sm text-muted-foreground">
            검색 결과가 없습니다.
          </div>
        ) : (
          visible.map((i) => <IdeaCard key={i.id} idea={i} />)
        )}
      </section>

      <button
        type="button"
        aria-label="아이디어 작성"
        title="아이디어 작성 — 준비 중 (#16)"
        className="fixed right-16 bottom-16 flex size-[72px] items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg hover:bg-primary/90"
      >
        <Plus className="size-9" aria-hidden />
      </button>
    </main>
  );
}
