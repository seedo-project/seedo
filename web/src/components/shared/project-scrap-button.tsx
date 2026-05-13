"use client";

import { Bookmark } from "lucide-react";
import { useState, useTransition } from "react";

import { useAuth } from "@/contexts/auth-context";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

export function ProjectScrapButton({
  projectId,
  initialCount,
  initialScrapped,
}: {
  projectId: number;
  initialCount: number;
  initialScrapped: boolean;
}) {
  const { user } = useAuth();
  const [count, setCount] = useState(initialCount);
  const [scrapped, setScrapped] = useState(initialScrapped);
  const [, startTransition] = useTransition();
  const [busy, setBusy] = useState(false);

  const onToggle = () => {
    if (busy) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }

    const prevScrapped = scrapped;
    const prevCount = count;
    const nextScrapped = !prevScrapped;
    setScrapped(nextScrapped);
    setCount(Math.max(0, prevCount + (nextScrapped ? 1 : -1)));
    setBusy(true);

    startTransition(async () => {
      try {
        const supabase = createClient();
        const { error } = nextScrapped
          ? await supabase
              .from("project_scraps")
              .insert({ user_id: user.id, project_id: projectId })
          : await supabase
              .from("project_scraps")
              .delete()
              .eq("user_id", user.id)
              .eq("project_id", projectId);

        if (error) {
          setScrapped(prevScrapped);
          setCount(prevCount);
          toast.error("스크랩 처리에 실패했습니다");
        }
      } catch {
        setScrapped(prevScrapped);
        setCount(prevCount);
        toast.error("스크랩 처리에 실패했습니다");
      } finally {
        setBusy(false);
      }
    });
  };

  const label = scrapped ? "스크랩 취소" : "스크랩";
  const color = scrapped ? "text-primary" : "text-muted-foreground";

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={scrapped}
      aria-label={`${label} (현재 ${count.toLocaleString("ko-KR")}회)`}
      className={`flex size-[60px] cursor-pointer flex-col items-center justify-center rounded-md transition-colors hover:bg-muted/50 disabled:cursor-not-allowed ${color}`}
      disabled={busy}
    >
      <Bookmark
        className="size-6"
        aria-hidden
        fill={scrapped ? "currentColor" : "none"}
      />
      <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
        {count.toLocaleString("ko-KR")}
      </span>
    </button>
  );
}
