"use client";

import { Loader2, Plus, Search, SearchX, Shuffle } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { EmptyState } from "@/components/shared/empty-state";
import { toast } from "@/lib/toast";
import type { ApiResponse } from "@/types/chat";
import type { SearchIdeasResponse } from "@/types/idea";

import { IdeaCard, type Idea } from "./idea-card";
import {
  IdeaPurchaseModal,
  type PurchasableIdea,
} from "./idea-purchase-modal";

type SearchState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; results: Idea[] };

function toIdea(row: SearchIdeasResponse): Idea {
  return {
    id: String(row.ideaId),
    variant: "default",
    tags: row.keywords,
    priceCredits: row.priceCredits,
  };
}

export function IdeaFeed({ ideas }: { ideas: Idea[] }) {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [search, setSearch] = useState<SearchState>({ kind: "idle" });
  const [purchaseTarget, setPurchaseTarget] = useState<PurchasableIdea | null>(
    null,
  );

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(searchQuery.trim()), 300);
    return () => clearTimeout(t);
  }, [searchQuery]);

  useEffect(() => {
    if (!debouncedQuery) {
      setSearch({ kind: "idle" });
      return;
    }
    const ac = new AbortController();
    setSearch({ kind: "loading" });
    (async () => {
      try {
        const res = await fetch(
          `/api/ideas/search?q=${encodeURIComponent(debouncedQuery)}`,
          { signal: ac.signal },
        );
        const body = (await res.json().catch(() => null)) as ApiResponse<
          SearchIdeasResponse[]
        > | null;
        if (!res.ok || !body || body.status !== "OK") {
          const message = body?.message ?? "검색에 실패했습니다";
          setSearch({ kind: "error", message });
          if (res.status >= 500) toast.error(message);
          return;
        }
        setSearch({ kind: "ok", results: body.data.map(toIdea) });
      } catch (err) {
        if (err instanceof Error && err.name === "AbortError") return;
        setSearch({ kind: "error", message: "검색 중 오류가 발생했습니다" });
      }
    })();
    return () => ac.abort();
  }, [debouncedQuery]);

  const showSearchResults = search.kind !== "idle";

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
            placeholder="원하는 것을 자연어로 검색해 보세요"
            aria-label="아이디어 검색"
            className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground/70 focus:outline-none"
          />
          {search.kind === "loading" ? (
            <Loader2
              className="size-6 animate-spin text-foreground"
              aria-hidden
            />
          ) : (
            <Search className="size-7 text-foreground" aria-hidden />
          )}
        </div>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="랜덤 아이디어 보기 — 준비 중"
          className="flex h-12 cursor-not-allowed items-center gap-2 rounded-full bg-primary px-6 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground opacity-50 shadow-md"
        >
          <Shuffle className="size-6" aria-hidden />
          랜덤 아이디어 보기
        </button>
      </div>

      <section className="mt-12 grid grid-cols-3 gap-5">
        {showSearchResults ? (
          <SearchResultsGrid
            state={search}
            onCardClick={handleCardClick}
          />
        ) : ideas.length === 0 ? (
          <div className="col-span-3">
            <EmptyState title="아직 등록된 아이디어가 없습니다" />
          </div>
        ) : (
          ideas.map((i) => (
            <IdeaCard key={i.id} idea={i} onClick={() => handleCardClick(i)} />
          ))
        )}
      </section>

      <Link
        href="/idea/write"
        aria-label="아이디어 작성"
        className="fixed right-[68px] bottom-[68px] flex size-[72px] items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg hover:bg-primary/90"
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

function SearchResultsGrid({
  state,
  onCardClick,
}: {
  state: SearchState;
  onCardClick: (idea: Idea) => void;
}) {
  if (state.kind === "loading") {
    return (
      <div className="col-span-3 flex items-center justify-center py-16">
        <Loader2
          className="size-6 animate-spin text-muted-foreground"
          aria-hidden
        />
      </div>
    );
  }
  if (state.kind === "error") {
    return (
      <div className="col-span-3">
        <EmptyState icon={SearchX} title={state.message} />
      </div>
    );
  }
  if (state.kind === "ok") {
    if (state.results.length === 0) {
      return (
        <div className="col-span-3">
          <EmptyState icon={SearchX} title="검색 결과가 없습니다" />
        </div>
      );
    }
    return (
      <>
        {state.results.map((i) => (
          <IdeaCard key={i.id} idea={i} onClick={() => onCardClick(i)} />
        ))}
      </>
    );
  }
  return null;
}
