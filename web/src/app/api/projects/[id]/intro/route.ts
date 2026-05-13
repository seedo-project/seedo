import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

/**
 * 프로젝트 소개 부분 수정 — Spring PATCH /api/v1/projects/{id}/intro 위임.
 * 4 항목 (coverImageUrl/title/description/guideMd) 중 변경된 것만 전달.
 */
export async function PATCH(
  req: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) {
    return NextResponse.json(
      { status: "ERROR", message: "잘못된 요청", data: null },
      { status: 400 },
    );
  }

  const body = await req.text();
  try {
    const res = await springFetch(`/api/v1/projects/${id}/intro`, {
      method: "PATCH",
      body,
    });
    const resBody = await res.text();
    const contentType = res.headers.get("content-type") ?? "application/json";
    return new NextResponse(resBody, {
      status: res.status,
      headers: { "Content-Type": contentType },
    });
  } catch (err) {
    console.error("project intro proxy failed", err);
    return NextResponse.json(
      { status: "ERROR", message: "서버에 연결할 수 없습니다", data: null },
      { status: 502 },
    );
  }
}
