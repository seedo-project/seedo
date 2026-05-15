"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

import { ChatComposer } from "@/components/idea/chat/chat-composer";
import { MessageList } from "@/components/idea/chat/message-list";
import type { ChatMessage } from "@/components/idea/chat/types";
import { Button } from "@/components/ui/button";
import { toast } from "@/lib/toast";
import type {
  ApiResponse,
  FinalizeChatSessionResponse,
  SendChatMessageResponse,
  StartChatSessionResponse,
} from "@/types/chat";

async function parseEnvelope<T>(res: Response): Promise<T> {
  let body: ApiResponse<T> | null = null;
  try {
    body = (await res.json()) as ApiResponse<T>;
  } catch {
    throw new Error(`요청 실패 (${res.status})`);
  }
  if (!res.ok || body.status !== "OK") {
    throw new Error(body.message ?? `요청 실패 (${res.status})`);
  }
  return body.data;
}

export default function IdeaWritePage() {
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const finalizingRef = useRef(false);

  const send = async (text: string) => {
    const userMsg: ChatMessage = {
      id: `u-${Date.now()}`,
      role: "USER",
      content: text,
    };
    setMessages((prev) => [...prev, userMsg]);
    setSending(true);
    try {
      let activeSessionId = sessionId;
      if (activeSessionId === null) {
        const startRes = await fetch("/api/chat-sessions", { method: "POST" });
        const started = await parseEnvelope<StartChatSessionResponse>(startRes);
        activeSessionId = started.sessionId;
        setSessionId(activeSessionId);
      }

      const sendRes = await fetch(`/api/chat-sessions/${activeSessionId}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: text }),
      });
      const reply = await parseEnvelope<SendChatMessageResponse>(sendRes);
      const assistantMsg: ChatMessage = {
        id: `a-${reply.assistantMessageId}`,
        role: "ASSISTANT",
        content: reply.content,
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "메시지 전송에 실패했습니다";
      toast.error(msg);
      setMessages((prev) => prev.filter((m) => m.id !== userMsg.id));
    } finally {
      setSending(false);
    }
  };

  const finalize = async () => {
    if (sessionId === null || finalizingRef.current) return;
    finalizingRef.current = true;
    setFinalizing(true);
    try {
      const res = await fetch(`/api/chat-sessions/${sessionId}/finalize`, {
        method: "POST",
      });
      const result = await parseEnvelope<FinalizeChatSessionResponse>(res);
      router.push(`/idea/${result.ideaId}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "마무리에 실패했습니다";
      toast.error(msg);
      finalizingRef.current = false;
      setFinalizing(false);
    }
  };

  if (messages.length === 0) {
    return (
      <main className="flex min-h-[calc(100svh-108px)] flex-col items-center justify-center px-4 md:px-[100px] pb-24">
        <div className="flex w-full max-w-[610px] flex-col items-center gap-10">
          <h1 className="text-2xl leading-[1.5] font-medium tracking-[-0.6px] text-foreground">
            아이디어가 있으신가요?
          </h1>
          <ChatComposer onSend={send} disabled={sending} autoFocus />
        </div>
      </main>
    );
  }

  const canFinalize = sessionId !== null && !sending && !finalizing;

  return (
    <main className="relative min-h-[calc(100svh-108px)]">
      <div className="sticky top-0 z-10 flex justify-end border-b border-transparent bg-white/90 px-4 md:px-[100px] py-4 backdrop-blur-sm">
        <Button
          variant="default"
          size="lg"
          onClick={finalize}
          disabled={!canFinalize}
        >
          {finalizing ? "마무리하는 중..." : "마무리하기"}
        </Button>
      </div>
      <div className="flex flex-col items-center px-4 md:px-[100px] pb-40 pt-6">
        <MessageList messages={messages} />
      </div>
      <div className="sticky bottom-9 flex justify-center pointer-events-none">
        <div className="pointer-events-auto">
          <ChatComposer
            onSend={send}
            disabled={sending || finalizing}
            placeholder={
              finalizing
                ? "마무리하는 중..."
                : sending
                  ? "응답을 기다리는 중..."
                  : "메시지를 입력하세요"
            }
          />
        </div>
      </div>
    </main>
  );
}
