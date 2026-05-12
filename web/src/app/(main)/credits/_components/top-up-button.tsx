"use client";

import { Button } from "@/components/ui/button";
import { toast } from "@/lib/toast";

export function TopUpButton() {
  // TODO: §8.1 PG webhook 충전 + 어드민 그랜트 자가 신청 API 연결 후 실제 충전 흐름으로 교체.
  return (
    <Button
      type="button"
      size="sm"
      onClick={() => toast.notReady("크레딧 충전 준비 중입니다")}
    >
      크레딧 충전하기
    </Button>
  );
}
