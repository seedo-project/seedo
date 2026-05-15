"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/contexts/auth-context";
import { toast } from "@/lib/toast";

type ApiResponse<T> = {
  status?: string;
  message?: string;
  data?: T;
};

type AdoptResultData = {
  projectId: number;
  rewardPaid: boolean;
  rewardTransactionId: number | null;
};

export function AdoptButton({
  ideaId,
  canAdopt,
  rewardCredits,
}: {
  ideaId: string;
  canAdopt: boolean;
  rewardCredits: number;
}) {
  const router = useRouter();
  const { refresh } = useAuth();
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (!canAdopt) {
    // 비활성 상태 — 같은 자리 유지 위해 disabled 버튼 출력.
    return (
      <button
        type="button"
        disabled
        aria-disabled="true"
        title="구매한 아이디어 또는 본인 작성 아이디어만 채택할 수 있습니다"
        className="flex h-9 cursor-not-allowed items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] whitespace-nowrap text-primary-foreground opacity-50"
      >
        프로젝트 시작하기
      </button>
    );
  }

  const onAdopt = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const res = await fetch(`/api/ideas/${ideaId}/adopt`, { method: "POST" });
      const body = (await res
        .json()
        .catch(() => ({}))) as ApiResponse<AdoptResultData>;
      if (!res.ok) {
        const msg = body.message ?? "프로젝트 시작에 실패했습니다";
        if (res.status === 409) {
          toast.error("이미 다른 사용자가 채택했습니다");
        } else {
          toast.error(msg);
        }
        return;
      }
      toast.success(
        body.data?.rewardPaid
          ? `프로젝트가 생성되었습니다 (작성자에게 ${rewardCredits} 크레딧 지급)`
          : "프로젝트가 생성되었습니다",
      );
      setOpen(false);
      await refresh();
      router.push("/feed/start");
      router.refresh();
    } catch {
      toast.error("네트워크 오류");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Button
        type="button"
        onClick={() => setOpen(true)}
        className="h-9"
      >
        프로젝트 시작하기
      </Button>
      <Dialog open={open} onOpenChange={(v) => !submitting && setOpen(v)}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>이 아이디어로 프로젝트를 시작할까요?</DialogTitle>
          </DialogHeader>
          <p className="text-sm leading-[1.5] text-muted-foreground">
            첫 채택자라면 작성자에게 {rewardCredits} 크레딧이 자동 지급됩니다.
            본인 아이디어를 채택하면 보상은 지급되지 않습니다.
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              size="sm"
              type="button"
              onClick={() => setOpen(false)}
            >
              취소
            </Button>
            <Button
              size="sm"
              type="button"
              onClick={onAdopt}
              disabled={submitting}
            >
              프로젝트 시작
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
