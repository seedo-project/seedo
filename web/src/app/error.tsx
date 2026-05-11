"use client";

import { ErrorState } from "@/components/shared/error-state";

export default function GlobalError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="min-h-svh">
      <ErrorState onReset={reset} />
    </main>
  );
}
