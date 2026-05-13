"use client";

import { useState } from "react";

import { ChatComposer } from "@/components/idea/chat/chat-composer";
import { MessageList } from "@/components/idea/chat/message-list";
import type { ChatMessage } from "@/components/idea/chat/types";

const MOCK_ASSISTANT_REPLY = `# 기술적으로 가능한가?

결론부터 말하면 "가능한 방식이 있다" 입니다. 다만 어떤 방식으로 최근 파일을 감지하느냐에 따라 구현 방법이 달라집니다.

## 방법 A — 폴더 감시 방식 (가장 현실적)

프로그램이 특정 작업 폴더를 계속 감시합니다.

예시 흐름: 사용자가 Photoshop에서 PSD 저장 → 우리 프로그램이 폴더 변경 이벤트 감지 → "최근 수정된 파일 = PSD 파일" → 프로그램에서 팝업`;

export default function IdeaWritePage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState(false);

  // TODO #161: Next Route Handler 프록시로 교체 — 현재는 mock 응답
  const send = async (text: string) => {
    const userMsg: ChatMessage = {
      id: `u-${Date.now()}`,
      role: "USER",
      content: text,
    };
    setMessages((prev) => [...prev, userMsg]);
    setSending(true);
    try {
      await new Promise((r) => setTimeout(r, 600));
      const assistantMsg: ChatMessage = {
        id: `a-${Date.now()}`,
        role: "ASSISTANT",
        content: MOCK_ASSISTANT_REPLY,
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } finally {
      setSending(false);
    }
  };

  if (messages.length === 0) {
    return (
      <main className="flex min-h-[calc(100svh-108px)] flex-col items-center justify-center px-[100px] pb-24">
        <div className="flex w-[610px] flex-col items-center gap-10">
          <h1 className="text-2xl leading-[1.5] font-medium tracking-[-0.6px] text-foreground">
            아이디어가 있으신가요?
          </h1>
          <ChatComposer onSend={send} disabled={sending} autoFocus />
        </div>
      </main>
    );
  }

  return (
    <main className="relative min-h-[calc(100svh-108px)]">
      <div className="flex flex-col items-center px-[100px] pb-40 pt-10">
        <MessageList messages={messages} />
      </div>
      <div className="sticky bottom-8 flex justify-center pointer-events-none">
        <div className="pointer-events-auto">
          <ChatComposer
            onSend={send}
            disabled={sending}
            placeholder={sending ? "응답을 기다리는 중..." : "메시지를 입력하세요"}
          />
        </div>
      </div>
    </main>
  );
}
