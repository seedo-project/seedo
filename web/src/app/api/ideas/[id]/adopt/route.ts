import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

/**
 * 아이디어 채택 → 프로젝트 생성 — Spring §8.3 트랜잭션 위임.
 */
export async function POST(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) {
    return NextResponse.json(
      { status: "ERROR", message: "잘못된 요청" },
      { status: 400 },
    );
  }

  const res = await springFetch(`/api/v1/ideas/${id}/adopt`, {
    method: "POST",
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}
