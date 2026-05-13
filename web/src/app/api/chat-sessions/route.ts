import { NextResponse } from "next/server";

import type { ApiResponse, StartChatSessionResponse } from "@/types/chat";

/**
 * 챗봇 세션 생성 — 백엔드 엔드포인트(POST /api/v1/chat-sessions) 머지 전까지 mock.
 *
 * TODO: 백엔드 머지 후 아래 mock 을 springFetch 호출로 교체.
 * const res = await springFetch("/api/v1/chat-sessions", { method: "POST" });
 * const body = await res.text();
 * return new NextResponse(body, { status: res.status, headers: { "Content-Type": "application/json" } });
 */
export async function POST() {
  const mock: ApiResponse<StartChatSessionResponse> = {
    status: "OK",
    message: null,
    data: {
      sessionId: Date.now(),
      createdAt: new Date().toISOString(),
    },
  };
  return NextResponse.json(mock, { status: 201 });
}
