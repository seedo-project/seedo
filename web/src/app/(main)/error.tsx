"use client";

import { ErrorState } from "@/components/shared/error-state";

export default function MainError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return <ErrorState onReset={reset} />;
}
