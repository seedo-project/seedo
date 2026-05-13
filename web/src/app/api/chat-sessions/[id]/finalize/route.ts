import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

export async function POST(
  _req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) {
    return NextResponse.json(
      { status: "ERROR", message: "잘못된 요청", data: null },
      { status: 400 },
    );
  }

  try {
    const res = await springFetch(`/api/v1/chat-sessions/${id}/finalize`, {
      method: "POST",
    });
    const body = await res.text();
    const contentType = res.headers.get("content-type") ?? "application/json";
    return new NextResponse(body, {
      status: res.status,
      headers: { "Content-Type": contentType },
    });
  } catch (err) {
    console.error("chat finalize proxy failed", err);
    return NextResponse.json(
      { status: "ERROR", message: "서버에 연결할 수 없습니다", data: null },
      { status: 502 },
    );
  }
}
