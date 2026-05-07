"use client";

import { Search } from "lucide-react";
import { useState } from "react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";

export const POST_TYPES = [
  { value: "FREE", label: "자유 게시판" },
  { value: "PROMO", label: "홍보 게시판" },
  { value: "BETA_RECRUIT", label: "베타 테스터 모집" },
  { value: "DEV_RECRUIT", label: "개발자 모집" },
] as const;

export type PostType = (typeof POST_TYPES)[number]["value"];

export type Post = {
  id: string;
  postType: PostType;
  title: string;
  preview: string;
  timestamp: string;
};

export function BoardView({ posts }: { posts: Post[] }) {
  const [selectedType, setSelectedType] = useState<PostType>("FREE");
  const [searchQuery, setSearchQuery] = useState("");

  const selectedLabel =
    POST_TYPES.find((t) => t.value === selectedType)?.label ?? "";
  const trimmed = searchQuery.trim().toLowerCase();
  const visiblePosts = posts
    .filter((p) => p.postType === selectedType)
    .filter((p) => {
      if (!trimmed) return true;
      return (
        p.title.toLowerCase().includes(trimmed) ||
        p.preview.toLowerCase().includes(trimmed)
      );
    });

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between">
        <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
          게시판
        </h1>
        <button
          type="button"
          className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
        >
          글 작성하기?
        </button>
      </div>

      <div className="mt-12 flex w-full items-center justify-between">
        <Select
          value={selectedType}
          onValueChange={(v) => setSelectedType(v as PostType)}
        >
          <SelectTrigger
            aria-label="게시판 카테고리"
            className="h-10 w-[190px] rounded-md border border-border bg-card px-3 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground"
          >
            <span>{selectedLabel}</span>
          </SelectTrigger>
          <SelectContent>
            {POST_TYPES.map((t) => (
              <SelectItem key={t.value} value={t.value}>
                {t.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex h-10 w-[295px] items-center rounded-md border border-border bg-card pr-2 pl-3">
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="게시판 글을 검색하세요."
            className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          <Search className="size-6 text-muted-foreground" aria-hidden />
        </div>
      </div>

      <section className="mt-12 flex flex-col gap-3">
        {visiblePosts.length === 0 ? (
          <div className="flex h-32 items-center justify-center rounded-md border border-border text-sm text-muted-foreground">
            해당하는 게시글이 없습니다.
          </div>
        ) : (
          visiblePosts.map((p) => <PostCard key={p.id} post={p} />)
        )}
      </section>
    </main>
  );
}

function PostCard({ post }: { post: Post }) {
  const typeLabel =
    POST_TYPES.find((t) => t.value === post.postType)?.label ?? post.postType;
  return (
    <article className="flex h-[132px] flex-col items-start rounded-md border border-border px-5 py-4">
      <div className="flex h-[101px] w-full flex-col gap-0.5">
        <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
          {typeLabel}
        </p>
        <h3 className="line-clamp-1 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
          {post.title}
        </h3>
        <p className="line-clamp-2 h-9 text-xs leading-[1.5] font-medium tracking-[-0.3px] whitespace-pre-line text-muted-foreground">
          {post.preview}
        </p>
        <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
          {post.timestamp}
        </p>
      </div>
    </article>
  );
}
