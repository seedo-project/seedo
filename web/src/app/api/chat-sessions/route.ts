import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

export async function POST() {
  try {
    const res = await springFetch("/api/v1/chat-sessions", { method: "POST" });
    const body = await res.text();
    const contentType = res.headers.get("content-type") ?? "application/json";
    return new NextResponse(body, {
      status: res.status,
      headers: { "Content-Type": contentType },
    });
  } catch (err) {
    console.error("chat session create proxy failed", err);
    return NextResponse.json(
      { status: "ERROR", message: "서버에 연결할 수 없습니다", data: null },
      { status: 502 },
    );
  }
}
