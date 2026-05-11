import Link from "next/link";

import { EmptyState } from "@/components/shared/empty-state";

export default function IdeaNotFound() {
  return (
    <main className="min-h-[calc(100svh-108px)]">
      <EmptyState
        title="아이디어를 찾을 수 없습니다"
        description="삭제되었거나 잘못된 주소일 수 있습니다"
        action={
          <Link
            href="/idea"
            className="inline-flex h-7 items-center rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium text-foreground hover:bg-muted"
          >
            아이디어 목록으로
          </Link>
        }
      />
    </main>
  );
}
