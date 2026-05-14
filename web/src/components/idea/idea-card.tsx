import type { KeyboardEvent } from "react";
import { Check } from "lucide-react";

import { IdeaCardTags } from "./idea-card-tags";
import { ClockIcon, CoinIcon } from "./idea-icons";

export type IdeaVariant = "default" | "purchased";

export type Idea =
  | {
      id: string;
      variant: "default";
      tags: string[];
      /** 작성 시각. 검색 결과처럼 시각 정보가 없으면 omit — clock 칩이 숨겨진다. */
      postedAt?: string;
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

export function IdeaCard({
  idea,
  onClick,
}: {
  idea: Idea;
  onClick?: () => void;
}) {
  const interactive = onClick
    ? {
        role: "button" as const,
        tabIndex: 0,
        onClick,
        onKeyDown: (e: KeyboardEvent) => {
          if (e.repeat) return;
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onClick();
          }
        },
        className: "cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/30",
      }
    : { className: "" };

  if (idea.variant === "purchased") {
    const accent = idea.purchasedByMe
      ? "text-muted-foreground"
      : "text-primary";
    return (
      <article
        {...interactive}
        className={`flex h-40 w-full flex-col justify-between rounded-xl bg-primary/5 px-5 py-4 ${interactive.className}`}
      >
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
    <article
      {...interactive}
      className={`flex h-40 w-full flex-col justify-between rounded-xl border border-border bg-card px-5 py-4 ${interactive.className}`}
    >
      <IdeaCardTags tags={idea.tags} />
      <div className="flex items-end gap-2 self-end text-muted-foreground">
        {idea.postedAt ? (
          <span className="flex items-end">
            <span className="flex size-5 items-center justify-center">
              <ClockIcon className="size-[13.333px]" />
            </span>
            <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px]">
              {idea.postedAt}
            </span>
          </span>
        ) : null}
        <span className="flex items-end">
          <span className="flex size-5 items-center justify-center">
            <CoinIcon className="size-[13.333px]" />
          </span>
          <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px]">
            {idea.priceCredits} 크레딧
          </span>
        </span>
      </div>
    </article>
  );
}
