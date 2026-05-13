"use client";

import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/contexts/auth-context";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

import type { PostType } from "./post-card";

const APPLY_LABEL: Partial<Record<PostType, string>> = {
  BETA_RECRUIT: "베타테스터 지원하기",
  DEV_RECRUIT: "개발자 지원하기",
};

const APPLY_CONFIRM: Partial<Record<PostType, string>> = {
  BETA_RECRUIT: "베타테스터로 지원하시겠어요?",
  DEV_RECRUIT: "개발자로 지원하시겠어요?",
};

export function PostApplyCta({
  postType,
  postId,
  initialApplied,
}: {
  postType: PostType;
  postId: string;
  initialApplied: boolean;
}) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [applied, setApplied] = useState(initialApplied);
  const [, startTransition] = useTransition();
  const [busy, setBusy] = useState(false);

  const label = APPLY_LABEL[postType];
  if (!label) return null;

  const onApply = () => {
    if (busy) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }
    setBusy(true);
    startTransition(async () => {
      try {
        const supabase = createClient();
        const { error } = await supabase
          .from("post_applications")
          .insert({ post_id: Number(postId), applicant_id: user.id });
        if (error) {
          if (error.code === "23505") {
            setApplied(true);
            toast.error("이미 지원했습니다");
          } else {
            toast.error("지원 처리에 실패했습니다");
          }
        } else {
          setApplied(true);
          toast.success("지원이 접수되었습니다");
        }
      } catch {
        toast.error("지원 처리에 실패했습니다");
      } finally {
        setBusy(false);
        setOpen(false);
      }
    });
  };

  const onCancel = () => {
    if (busy) return;
    if (!user) return;
    if (!confirm("지원을 취소할까요?")) return;
    setBusy(true);
    startTransition(async () => {
      try {
        const supabase = createClient();
        const { error } = await supabase
          .from("post_applications")
          .delete()
          .eq("post_id", Number(postId))
          .eq("applicant_id", user.id);
        if (error) {
          toast.error("지원 취소에 실패했습니다");
        } else {
          setApplied(false);
          toast.success("지원이 취소되었습니다");
        }
      } catch {
        toast.error("지원 취소에 실패했습니다");
      } finally {
        setBusy(false);
      }
    });
  };

  if (applied) {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={onCancel}
        disabled={busy}
        className="h-9"
      >
        지원 취소
      </Button>
    );
  }

  return (
    <>
      <Button
        type="button"
        disabled={busy}
        onClick={() => setOpen(true)}
        className="h-9"
      >
        {label}
      </Button>

      <Dialog open={open} onOpenChange={(v) => !busy && setOpen(v)}>
        <DialogContent className="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle>{APPLY_CONFIRM[postType]}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            지원 후에는 작성자가 내 프로필을 확인할 수 있습니다.
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setOpen(false)}
              type="button"
              disabled={busy}
            >
              취소
            </Button>
            <Button
              size="sm"
              onClick={onApply}
              type="button"
              disabled={busy}
              data-post-id={postId}
            >
              지원하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
