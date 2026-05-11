import Link from "next/link";

import { EmptyState } from "@/components/shared/empty-state";

export default function FeedNotFound() {
  return (
    <main className="min-h-[calc(100svh-108px)]">
      <EmptyState
        title="프로젝트를 찾을 수 없습니다"
        description="삭제되었거나 잘못된 주소일 수 있습니다"
        action={
          <Link
            href="/feed"
            className="inline-flex h-7 items-center rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium text-foreground hover:bg-muted"
          >
            피드로
          </Link>
        }
      />
    </main>
  );
}
