"use client";

import { Star } from "lucide-react";
import { useState, useTransition } from "react";

import { useAuth } from "@/contexts/auth-context";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

export type HypeTarget =
  | { kind: "idea"; id: number }
  | { kind: "project"; id: number };

export function HypeButton({
  target,
  initialCount,
  initialHyped,
}: {
  target: HypeTarget;
  initialCount: number;
  initialHyped: boolean;
}) {
  const { user } = useAuth();
  const [count, setCount] = useState(initialCount);
  const [hyped, setHyped] = useState(initialHyped);
  const [, startTransition] = useTransition();
  const [busy, setBusy] = useState(false);

  const onToggle = () => {
    if (busy) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }

    const prevHyped = hyped;
    const prevCount = count;
    const nextHyped = !prevHyped;
    setHyped(nextHyped);
    setCount(prevCount + (nextHyped ? 1 : -1));
    setBusy(true);

    startTransition(async () => {
      const supabase = createClient();
      const targetCol = target.kind === "idea" ? "idea_id" : "project_id";

      const { error } = nextHyped
        ? await supabase
            .from("hypes")
            .insert({ user_id: user.id, [targetCol]: target.id })
        : await supabase
            .from("hypes")
            .delete()
            .eq("user_id", user.id)
            .eq(targetCol, target.id);

      if (error) {
        setHyped(prevHyped);
        setCount(prevCount);
        toast.error("좋아요 처리에 실패했습니다");
      }
      setBusy(false);
    });
  };

  const label = hyped ? "좋아요 취소" : "좋아요";
  const color = hyped ? "text-primary" : "text-muted-foreground";

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={hyped}
      aria-label={`${label} (현재 ${count.toLocaleString("ko-KR")}회)`}
      className={`flex size-[60px] cursor-pointer flex-col items-center justify-center rounded-md transition-colors hover:bg-muted/50 disabled:cursor-not-allowed ${color}`}
      disabled={busy}
    >
      <Star
        className="size-6"
        aria-hidden
        fill={hyped ? "currentColor" : "none"}
      />
      <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
        {count.toLocaleString("ko-KR")}
      </span>
    </button>
  );
}
