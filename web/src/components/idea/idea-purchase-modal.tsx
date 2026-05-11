"use client";

import { useRouter } from "next/navigation";

import { ChipIdea } from "@/components/idea/chip-idea";
import { CoinIcon } from "@/components/idea/idea-icons";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export type PurchasableIdea = {
  id: string;
  tags: string[];
  priceCredits: number;
};

export function IdeaPurchaseModal({
  idea,
  onClose,
}: {
  idea: PurchasableIdea | null;
  onClose: () => void;
}) {
  const router = useRouter();
  const open = idea !== null;

  const handlePurchase = () => {
    if (!idea) return;
    // TODO: Spring API 호출 (§8.2 아이디어 구매 트랜잭션) — 지금은 즉시 상세로 이동
    onClose();
    router.push(`/idea/${idea.id}`);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="flex flex-col gap-5 px-5 pb-5 sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="text-center text-xl font-bold tracking-[-0.5px] text-foreground">
            아이디어 구매하기?
          </DialogTitle>
        </DialogHeader>

        {idea && (
          <div className="flex w-[400px] max-w-full flex-wrap gap-x-2 gap-y-2.5 rounded-md border border-border p-4">
            {idea.tags.map((t) => (
              <ChipIdea key={t} label={t} />
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
          className="flex h-12 items-center justify-center gap-2 self-center rounded-md bg-foreground px-6 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-background hover:bg-[#3f3f46]"
        >
          <CoinIcon className="size-3 text-yellow-400" />
          {idea?.priceCredits ?? 0} 크레딧 결제
        </button>
      </DialogContent>
    </Dialog>
  );
}
