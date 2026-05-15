"use client";

import { ArrowUp } from "lucide-react";
import { useState, type KeyboardEvent } from "react";

type Props = {
  onSend: (text: string) => void;
  disabled?: boolean;
  placeholder?: string;
  autoFocus?: boolean;
};

export function ChatComposer({
  onSend,
  disabled = false,
  placeholder = "아이디어를 한 줄로 설명해주세요",
  autoFocus = false,
}: Props) {
  const [value, setValue] = useState("");
  const canSend = !disabled && value.trim().length > 0;

  const submit = () => {
    if (!canSend) return;
    onSend(value.trim());
    setValue("");
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      className="flex h-12 w-[610px] items-center justify-between rounded-full border border-[#d4d4d8] bg-white py-1.5 pr-1.5 pl-5 shadow-md"
    >
      <input
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        aria-label="메시지 입력"
        className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-[#27272a] placeholder:text-muted-foreground/70 focus:outline-none disabled:opacity-60"
      />
      <button
        type="submit"
        disabled={!canSend}
        aria-label="보내기"
        className="flex size-9 items-center justify-center rounded-full bg-[#e4e4e7] text-muted-foreground transition-colors enabled:bg-[#27272a] enabled:text-white enabled:hover:bg-[#3f3f46]"
      >
        <ArrowUp className="size-5" aria-hidden />
      </button>
    </form>
  );
}
