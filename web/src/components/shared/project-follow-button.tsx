"use client";

import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/auth-context";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

export function ProjectFollowButton({
  projectId,
  initialCount,
  initialFollowed,
}: {
  projectId: number;
  initialCount: number;
  initialFollowed: boolean;
}) {
  const { user } = useAuth();
  const [count, setCount] = useState(initialCount);
  const [followed, setFollowed] = useState(initialFollowed);
  const [, startTransition] = useTransition();
  const [busy, setBusy] = useState(false);

  const onToggle = () => {
    if (busy) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }

    const prevFollowed = followed;
    const prevCount = count;
    const nextFollowed = !prevFollowed;
    setFollowed(nextFollowed);
    setCount(Math.max(0, prevCount + (nextFollowed ? 1 : -1)));
    setBusy(true);

    startTransition(async () => {
      try {
        const supabase = createClient();
        const { error } = nextFollowed
          ? await supabase
              .from("project_follows")
              .insert({ user_id: user.id, project_id: projectId })
          : await supabase
              .from("project_follows")
              .delete()
              .eq("user_id", user.id)
              .eq("project_id", projectId);

        if (error) {
          setFollowed(prevFollowed);
          setCount(prevCount);
          toast.error("팔로우 처리에 실패했습니다");
        }
      } catch {
        setFollowed(prevFollowed);
        setCount(prevCount);
        toast.error("팔로우 처리에 실패했습니다");
      } finally {
        setBusy(false);
      }
    });
  };

  return (
    <Button
      type="button"
      variant={followed ? "outline" : "default"}
      size="sm"
      onClick={onToggle}
      disabled={busy}
      aria-pressed={followed}
      className="h-9"
    >
      {followed ? "팔로잉" : "팔로우"}
      <span className="ml-1 text-xs opacity-80">
        {count.toLocaleString("ko-KR")}
      </span>
    </Button>
  );
}
