import { notFound } from "next/navigation";

import { createClient } from "@/lib/supabase/server";
import { formatPublishedDateKo } from "@/lib/format";

export type IdeaDetail = {
  id: string;
  title: string;
  authorName: string;
  publishedAt: string;
  body: string;
  isAuthor: boolean;
  isPurchased: boolean;
};

export async function fetchIdeaDetail(id: string): Promise<IdeaDetail> {
  const supabase = await createClient();
  const numericId = Number(id);
  if (!Number.isFinite(numericId)) notFound();

  const { data: idea } = await supabase
    .from("ideas")
    .select(
      `id,
       author_id,
       status,
       created_at,
       current_version_id,
       idea_documents!ideas_current_version_id_fkey ( title, content_md )`,
    )
    .eq("id", numericId)
    .maybeSingle();

  if (!idea) notFound();

  const {
    data: { user: authUser },
  } = await supabase.auth.getUser();
  const isAuthor = !!authUser && authUser.id === idea.author_id;

  // 작성자 닉네임은 public_profiles 뷰 (V7) 로 별도 조회 — view 라 embed 불가.
  const [{ data: author }, purchaseResult] = await Promise.all([
    supabase
      .from("public_profiles")
      .select("nickname")
      .eq("id", idea.author_id)
      .maybeSingle(),
    authUser
      ? supabase
          .from("idea_purchases")
          .select("id")
          .eq("idea_id", numericId)
          .eq("buyer_id", authUser.id)
          .maybeSingle()
      : Promise.resolve({ data: null }),
  ]);
  const isPurchased = !!purchaseResult.data;

  const doc = Array.isArray(idea.idea_documents)
    ? idea.idea_documents[0]
    : idea.idea_documents;

  // RLS 가 본문 차단 시 doc null — 구매 안내 메시지로 대체.
  const body =
    doc?.content_md ??
    "본문은 아이디어를 구매한 사용자와 작성자만 열람할 수 있습니다.";
  const title = doc?.title ?? "비공개 아이디어";

  return {
    id: String(idea.id),
    title,
    authorName: author?.nickname ?? "익명",
    publishedAt: formatPublishedDateKo(idea.created_at),
    body,
    isAuthor,
    isPurchased,
  };
}
