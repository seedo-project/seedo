import { createClient } from "@/lib/supabase/server";

export type CommentTarget = "idea" | "project" | "post";

export type CommentItem = {
  id: number;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  isAuthor: boolean;
};

function tableFor(target: CommentTarget) {
  return target === "idea"
    ? "idea_comments"
    : target === "project"
      ? "project_comments"
      : "post_comments";
}

function targetColFor(target: CommentTarget) {
  return target === "idea"
    ? "idea_id"
    : target === "project"
      ? "project_id"
      : "post_id";
}

export async function fetchComments(
  target: CommentTarget,
  targetId: number,
): Promise<CommentItem[]> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  const { data: rows } = await supabase
    .from(tableFor(target))
    .select("id, author_id, content, created_at, updated_at")
    .eq(targetColFor(target), targetId)
    .is("deleted_at", null)
    .order("created_at", { ascending: true });

  const comments = rows ?? [];
  if (comments.length === 0) return [];

  const authorIds = [...new Set(comments.map((c) => c.author_id))];
  const { data: profiles } = await supabase
    .from("public_profiles")
    .select("id, nickname")
    .in("id", authorIds);

  const nameById = new Map(
    (profiles ?? []).map((p) => [p.id, p.nickname ?? "익명"]),
  );

  return comments.map((c) => ({
    id: Number(c.id),
    authorId: c.author_id,
    authorName: nameById.get(c.author_id) ?? "익명",
    content: c.content,
    createdAt: c.created_at,
    updatedAt: c.updated_at,
    isAuthor: !!user && user.id === c.author_id,
  }));
}

export { tableFor as commentTable, targetColFor as commentTargetCol };
