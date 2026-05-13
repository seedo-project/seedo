import { commentTable, commentTargetCol } from "@/lib/comments-target";
import type { CommentTarget } from "@/lib/comments-target";
import { createClient } from "@/lib/supabase/server";

export type { CommentTarget };

export type CommentItem = {
  id: number;
  authorId: string;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  isAuthor: boolean;
};

export async function fetchComments(
  target: CommentTarget,
  targetId: number,
): Promise<CommentItem[]> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  const { data: rows, error: rowsError } = await supabase
    .from(commentTable(target))
    .select("id, author_id, content, created_at, updated_at")
    .eq(commentTargetCol(target), targetId)
    .is("deleted_at", null)
    .order("created_at", { ascending: true });
  if (rowsError) throw rowsError;

  const comments = rows ?? [];
  if (comments.length === 0) return [];

  const authorIds = [...new Set(comments.map((c) => c.author_id))];
  const { data: profiles, error: profilesError } = await supabase
    .from("public_profiles")
    .select("id, nickname")
    .in("id", authorIds);
  if (profilesError) throw profilesError;

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
