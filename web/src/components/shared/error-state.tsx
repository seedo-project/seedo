"use client";

import { AlertTriangle } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";

type ErrorStateProps = {
  title?: string;
  description?: string;
  onReset?: () => void;
  homeHref?: string;
};

export function ErrorState({
  title = "문제가 발생했습니다",
  description = "잠시 후 다시 시도해주세요",
  onReset,
  homeHref = "/idea",
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex min-h-[calc(100svh-108px)] flex-col items-center justify-center gap-4 px-6 text-center"
    >
      <AlertTriangle className="size-10 text-destructive" aria-hidden />
      <div className="flex flex-col gap-1">
        <p className="text-base font-semibold text-foreground">{title}</p>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      <div className="flex items-center gap-2 pt-1">
        {onReset ? (
          <Button onClick={onReset} variant="default" size="sm">
            다시 시도
          </Button>
        ) : null}
        <Link
          href={homeHref}
          className="inline-flex h-7 items-center rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium text-foreground hover:bg-muted"
        >
          홈으로
        </Link>
      </div>
    </div>
  );
}
