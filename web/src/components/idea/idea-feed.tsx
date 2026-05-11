"use client";

import { Plus, Search, SearchX, Shuffle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { EmptyState } from "@/components/shared/empty-state";

import { IdeaCard, type Idea } from "./idea-card";
import {
  IdeaPurchaseModal,
  type PurchasableIdea,
} from "./idea-purchase-modal";

export function IdeaFeed({ ideas }: { ideas: Idea[] }) {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState("");
  const [purchaseTarget, setPurchaseTarget] = useState<PurchasableIdea | null>(
    null,
  );

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

  const handleCardClick = (idea: Idea) => {
    if (idea.variant === "purchased") {
      router.push(`/idea/${idea.id}`);
      return;
    }
    setPurchaseTarget({
      id: idea.id,
      tags: idea.tags,
      priceCredits: idea.priceCredits,
    });
  };

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
          disabled
          aria-disabled="true"
          title="랜덤 아이디어 보기 — 준비 중"
          className="flex h-12 cursor-not-allowed items-center gap-2 rounded-full bg-primary px-6 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground opacity-50 shadow-md"
        >
          <Shuffle className="size-6" aria-hidden />
          랜덤 아이디어 보기?
        </button>
      </div>

      <section className="mt-12 grid grid-cols-3 gap-5">
        {visible.length === 0 ? (
          <div className="col-span-3">
            <EmptyState icon={SearchX} title="검색 결과가 없습니다" />
          </div>
        ) : (
          visible.map((i) => (
            <IdeaCard key={i.id} idea={i} onClick={() => handleCardClick(i)} />
          ))
        )}
      </section>

      <Link
        href="/idea/write"
        aria-label="아이디어 작성"
        className="fixed right-16 bottom-16 flex size-[72px] items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg hover:bg-primary/90"
      >
        <Plus className="size-9" aria-hidden />
      </Link>

      <IdeaPurchaseModal
        idea={purchaseTarget}
        onClose={() => setPurchaseTarget(null)}
      />
    </main>
  );
}
