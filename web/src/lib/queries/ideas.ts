import { createClient } from "@/lib/supabase/server";
import type { Idea } from "@/components/idea/idea-card";
import { formatRelativeKo } from "@/lib/format";

/**
 * 첫 문단(혹은 첫 두 줄)을 가볍게 요약하는 헬퍼.
 * 본문 첫 줄이 "# 헤딩" 이면 그 다음 본문 두 줄을 노출.
 */
function previewFromMarkdown(content: string): string {
  const lines = content
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));
  return lines.slice(0, 2).join(" ").slice(0, 140);
}

export async function fetchIdeaFeed(): Promise<Idea[]> {
  const supabase = await createClient();

  const { data: { user: authUser } } = await supabase.auth.getUser();
  const userId = authUser?.id ?? null;

  // 공개 SELECT 정책: PUBLISHED + not deleted (V6 RLS)
  // ideas → current_version_id 로 idea_documents 1:1, idea_embeddings 1:1
  const { data, error } = await supabase
    .from("ideas")
    .select(
      `id,
       author_id,
       price_credits,
       created_at,
       current_version_id,
       idea_embeddings ( keywords ),
       idea_documents!ideas_current_version_id_fkey ( title, content_md )`,
    )
    .eq("status", "PUBLISHED")
    .is("deleted_at", null)
    .order("created_at", { ascending: false });
  if (error) throw error;
  if (!data) return [];

  // 본인이 산 idea_id set (RLS 로 본인 row 만 반환)
  let purchasedSet = new Set<number>();
  if (userId) {
    const { data: purchases } = await supabase
      .from("idea_purchases")
      .select("idea_id")
      .eq("buyer_id", userId);
    purchasedSet = new Set((purchases ?? []).map((p) => p.idea_id));
  }

  return data.map((row): Idea => {
    const id = String(row.id);
    const purchased = purchasedSet.has(row.id);
    // 작성자 본인도 본문 열람 권한 보유 — V6 의 ideas_author_select / idea_documents_buyer_or_author_select
    // RLS 와 같은 사상. 피드에서도 구매한 카드와 동일하게 본문 미리보기 + 클릭 시 상세 페이지로 (#183).
    const isAuthor = userId !== null && row.author_id === userId;
    const doc = Array.isArray(row.idea_documents)
      ? row.idea_documents[0]
      : row.idea_documents;
    const emb = Array.isArray(row.idea_embeddings)
      ? row.idea_embeddings[0]
      : row.idea_embeddings;

    if ((purchased || isAuthor) && doc) {
      return {
        id,
        variant: "purchased",
        title: doc.title,
        description: previewFromMarkdown(doc.content_md ?? ""),
        // purchasedByMe 의미를 \"본문 열람 권한 있음\" 으로 확장 — 작성자도 true. UI 의 \"내 카드\" vs \"내가 산 카드\"
        // 구분이 필요해지면 별도 ownedByMe 플래그 도입을 후속으로 고려.
        purchasedByMe: true,
      };
    }
    const keywords: string[] = emb?.keywords ?? [];
    return {
      id,
      variant: "default",
      tags: keywords,
      postedAt: formatRelativeKo(row.created_at),
      priceCredits: row.price_credits,
    };
  });
}
