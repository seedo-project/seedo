import { FileQuestion } from "lucide-react";
import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 px-6 text-center">
      <FileQuestion className="size-12 text-muted-foreground/60" aria-hidden />
      <div className="flex flex-col gap-1">
        <p className="text-base font-semibold text-foreground">
          페이지를 찾을 수 없습니다
        </p>
        <p className="text-sm text-muted-foreground">
          주소가 정확한지 확인해주세요
        </p>
      </div>
      <Link
        href="/idea"
        className="inline-flex h-7 items-center rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium text-foreground hover:bg-muted"
      >
        홈으로
      </Link>
    </main>
  );
}
