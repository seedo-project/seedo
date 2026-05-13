import { NextResponse } from "next/server";

import { springFetch } from "@/lib/api/client";

/**
 * 아이디어 하이브리드 검색 — Spring §RRF 위임 (#138).
 * q 비어있으면 400. limit 은 백엔드가 [1, 50] 클램프.
 */
export async function GET(req: Request) {
  const { searchParams } = new URL(req.url);
  const q = searchParams.get("q")?.trim();
  if (!q) {
    return NextResponse.json(
      { status: "ERROR", message: "검색어를 입력해 주세요", data: null },
      { status: 400 },
    );
  }

  const rawLimit = searchParams.get("limit");
  let limitParam = "";
  if (rawLimit !== null) {
    if (!/^[1-9]\d*$/.test(rawLimit)) {
      return NextResponse.json(
        { status: "ERROR", message: "limit 은 양의 정수여야 합니다", data: null },
        { status: 400 },
      );
    }
    limitParam = `&limit=${rawLimit}`;
  }
  const path = `/api/v1/ideas/search?q=${encodeURIComponent(q)}${limitParam}`;

  try {
    const res = await springFetch(path, { method: "GET" });
    const body = await res.text();
    const contentType = res.headers.get("content-type") ?? "application/json";
    return new NextResponse(body, {
      status: res.status,
      headers: { "Content-Type": contentType },
    });
  } catch (err) {
    console.error("idea search proxy failed", err);
    return NextResponse.json(
      { status: "ERROR", message: "서버에 연결할 수 없습니다", data: null },
      { status: 502 },
    );
  }
}
