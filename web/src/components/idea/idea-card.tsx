import { Check, Clock, Coins } from "lucide-react";

import { ChipIdea } from "./chip-idea";

export type IdeaVariant = "default" | "purchased";

export type Idea =
  | {
      id: string;
      variant: "default";
      tags: string[];
      postedAt: string;
      priceCredits: number;
    }
  | {
      id: string;
      variant: "purchased";
      title: string;
      description: string;
      // 본인이 구매한 경우 회색, 타인이 구매한 경우 primary 색
      purchasedByMe: boolean;
    };

export function IdeaCard({ idea }: { idea: Idea }) {
  if (idea.variant === "purchased") {
    const accent = idea.purchasedByMe
      ? "text-muted-foreground"
      : "text-primary";
    return (
      <article className="flex h-40 w-full flex-col justify-between rounded-xl bg-primary/5 px-5 py-4">
        <div className="flex w-full flex-col gap-1">
          <h3 className="line-clamp-1 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
            {idea.title}
          </h3>
          <p className="line-clamp-3 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            {idea.description}
          </p>
        </div>
        <div className={`flex items-center gap-1 self-end ${accent}`}>
          <Check className="size-5" aria-hidden />
          <span className="text-sm leading-[1.3] font-semibold tracking-[-0.35px]">
            구매됨
          </span>
        </div>
      </article>
    );
  }

  return (
    <article className="flex h-40 w-full flex-col justify-between rounded-xl border border-border bg-card px-5 py-4">
      <div className="flex w-full flex-wrap gap-x-2 gap-y-2.5">
        {idea.tags.map((t) => (
          <ChipIdea key={t} label={t} />
        ))}
      </div>
      <div className="flex items-center gap-2 self-end text-muted-foreground">
        <Clock className="size-5" aria-hidden />
        <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px]">
          {idea.postedAt}
        </span>
        <Coins className="size-5" aria-hidden />
        <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px]">
          {idea.priceCredits} 크레딧
        </span>
      </div>
    </article>
  );
}
