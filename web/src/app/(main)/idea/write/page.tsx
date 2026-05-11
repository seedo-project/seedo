"use client";

import { ArrowUp } from "lucide-react";
import { useState } from "react";

import { toast } from "@/lib/toast";

export default function IdeaWritePage() {
  const [message, setMessage] = useState("");

  const handleSend = () => {
    if (!message.trim()) return;
    // TODO: 챗봇 finalize 시작 (§8.4) — Spring API + LLM 연동 별도 작업
    toast.notReady("챗봇 연결 준비 중입니다");
  };

  return (
    <main className="flex min-h-[calc(100svh-108px)] flex-col items-center justify-center px-[100px] pb-24">
      <div className="flex w-[610px] flex-col items-center gap-10">
        <h1 className="text-2xl leading-[1.5] font-medium tracking-[-0.6px] text-foreground">
          아이디어가 있으신가요?
        </h1>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSend();
          }}
          className="flex h-12 w-full items-center justify-between rounded-full border border-input bg-card py-1.5 pr-1.5 pl-5 shadow-md"
        >
          <input
            type="text"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="아이디어를 한 줄로 설명해주세요"
            aria-label="아이디어 입력"
            className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground/70 focus:outline-none"
          />
          <button
            type="submit"
            disabled={!message.trim()}
            aria-label="보내기"
            className="flex size-9 items-center justify-center rounded-full bg-[#e4e4e7] text-muted-foreground transition-colors enabled:bg-foreground enabled:text-background enabled:hover:bg-[#3f3f46]"
          >
            <ArrowUp className="size-5" aria-hidden />
          </button>
        </form>
      </div>
    </main>
  );
}
