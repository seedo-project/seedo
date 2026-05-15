"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { ChipIdea } from "@/components/idea/chip-idea";
import { CoinIcon } from "@/components/idea/idea-icons";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuth } from "@/contexts/auth-context";
import { toast } from "@/lib/toast";

export type PurchasableIdea = {
  id: string;
  tags: string[];
  priceCredits: number;
};

type ApiError = { status?: string; message?: string };

export function IdeaPurchaseModal({
  idea,
  onClose,
}: {
  idea: PurchasableIdea | null;
  onClose: () => void;
}) {
  const router = useRouter();
  const { refresh } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const open = idea !== null;

  const handlePurchase = async () => {
    if (!idea || submitting) return;
    setSubmitting(true);
    try {
      const res = await fetch(`/api/ideas/${idea.id}/purchase`, {
        method: "POST",
      });
      if (!res.ok) {
        const body = (await res.json().catch(() => ({}))) as ApiError;
        const msg = body.message ?? "구매에 실패했습니다";
        // 잔액 부족은 충전 안내 토스트 (별도 액션 버튼은 추후)
        if (res.status === 400 && msg.includes("잔액")) {
          toast.error("크레딧이 부족합니다");
        } else if (res.status === 409) {
          toast.info("이미 구매한 아이디어입니다");
          onClose();
          router.push(`/idea/${idea.id}`);
          return;
        } else {
          toast.error(msg);
        }
        return;
      }
      toast.success("구매가 완료되었습니다");
      onClose();
      await refresh();
      router.push(`/idea/${idea.id}`);
      router.refresh();
    } catch {
      toast.error("네트워크 오류");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(v) => !v && !submitting && onClose()}>
      <DialogContent className="flex flex-col gap-5 px-5 pb-5 sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="text-center text-xl font-bold tracking-[-0.5px] text-foreground">
            아이디어 구매하기?
          </DialogTitle>
        </DialogHeader>

        {idea && (
          <div className="flex w-full max-w-[400px] flex-wrap gap-x-2 gap-y-2.5 rounded-md border border-border p-4">
            {idea.tags.map((t, i) => (
              <ChipIdea key={`${t}-${i}`} label={t} />
            ))}
          </div>
        )}

        <p className="px-3 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
          {idea
            ? `${idea.priceCredits} 크레딧을 지불하고 위 아이디어 전문을 확인할까요?`
            : ""}
        </p>

        <button
          type="button"
          onClick={handlePurchase}
          disabled={submitting}
          className="flex h-12 items-center justify-center gap-2 self-center rounded-md bg-foreground px-6 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-background hover:bg-[#3f3f46] disabled:opacity-50"
        >
          <CoinIcon className="size-3 text-yellow-400" />
          {idea?.priceCredits ?? 0} 크레딧 결제
        </button>
      </DialogContent>
    </Dialog>
  );
}
