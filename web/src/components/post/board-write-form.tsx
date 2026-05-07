"use client";

import { useState } from "react";

import { POST_TYPES, type PostType } from "@/components/post/board-view";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";

export function BoardWriteForm() {
  const [title, setTitle] = useState("");
  const [postType, setPostType] = useState<PostType | "">("");
  const [body, setBody] = useState("");

  const selectedLabel =
    POST_TYPES.find((t) => t.value === postType)?.label ?? "";
  const isValid =
    title.trim().length > 0 && postType !== "" && body.trim().length > 0;

  const handleDraft = () => {
    // TODO: Spring API 연결 — DRAFT 저장
  };

  const handlePublish = () => {
    if (!isValid) return;
    // TODO: Spring API 연결 — 발행
  };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <header className="flex w-full items-center justify-between">
        <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
          게시물 게시하기?
        </h1>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleDraft}
            className="flex h-9 items-center justify-center rounded-md bg-[#e4e4e7] px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-muted-foreground hover:bg-[#d4d4d8]"
          >
            임시 저장
          </button>
          <button
            type="button"
            onClick={handlePublish}
            disabled={!isValid}
            className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            게시하기
          </button>
        </div>
      </header>

      <div className="mt-8 flex w-full flex-col gap-3">
        <div className="flex w-full items-start gap-2">
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="게시물 제목을 입력해 주세요."
            aria-label="게시물 제목"
            className="h-10 flex-1 rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          <Select
            value={postType === "" ? undefined : postType}
            onValueChange={(v) => setPostType(v as PostType)}
          >
            <SelectTrigger
              aria-label="게시판 선택"
              className="h-10 w-[190px] rounded-md border border-input bg-card px-3 text-sm leading-[1.5] tracking-[-0.35px] text-muted-foreground"
            >
              <span>{selectedLabel || "게시판 선택..."}</span>
            </SelectTrigger>
            <SelectContent>
              {POST_TYPES.map((t) => (
                <SelectItem key={t.value} value={t.value}>
                  {t.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="내용을 입력해 주세요."
          aria-label="게시물 내용"
          className="h-[480px] w-full resize-none rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
        />
      </div>
    </main>
  );
}
