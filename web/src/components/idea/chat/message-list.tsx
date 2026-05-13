"use client";

import { useEffect, useRef } from "react";

import { AssistantMessage } from "./assistant-message";
import { UserMessage } from "./user-message";
import type { ChatMessage } from "./types";

export function MessageList({ messages }: { messages: ChatMessage[] }) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length]);

  return (
    <div className="flex w-[820px] flex-col gap-12">
      {messages.map((m) =>
        m.role === "USER" ? (
          <UserMessage key={m.id} content={m.content} />
        ) : (
          <AssistantMessage key={m.id} content={m.content} />
        ),
      )}
      <div ref={bottomRef} />
    </div>
  );
}
