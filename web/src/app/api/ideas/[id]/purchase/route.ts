import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

/**
 * 아이디어 구매 — Spring §8.2 트랜잭션 위임.
 * 클라이언트 (modal) 는 이 라우트만 호출, JWT 첨부와 base URL 은 springFetch 가 처리.
 */
export async function POST(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) {
    return NextResponse.json({ error: "잘못된 요청" }, { status: 400 });
  }

  const res = await springFetch(`/api/v1/ideas/${id}/purchase`, {
    method: "POST",
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}
