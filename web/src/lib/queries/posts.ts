import { notFound } from "next/navigation";

import { formatPublishedDateKo, formatRelativeKo } from "@/lib/format";
import { createClient } from "@/lib/supabase/server";
import type { Post, PostType } from "@/components/post/post-card";

export type PostDetail = {
  id: string;
  postType: PostType;
  title: string;
  authorId: string;
  authorName: string;
  publishedAt: string;
  body: string;
  isAuthor: boolean;
};

function previewFromBody(body: string): string {
  return body
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .slice(0, 2)
    .join("\n");
}

export async function fetchPosts(): Promise<Post[]> {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from("posts")
    .select("id, post_type, title, body, created_at")
    .eq("status", "PUBLISHED")
    .is("deleted_at", null)
    .order("created_at", { ascending: false })
    .limit(100);
  if (error) throw error;

  return (data ?? []).map((p) => ({
    id: String(p.id),
    postType: p.post_type as PostType,
    title: p.title,
    preview: previewFromBody(p.body),
    timestamp: formatRelativeKo(p.created_at),
    createdAt: p.created_at,
  }));
}

export async function fetchPostDetail(id: string): Promise<PostDetail> {
  const supabase = await createClient();
  if (!/^\d+$/.test(id)) notFound();
  const numericId = Number(id);
  if (!Number.isSafeInteger(numericId) || numericId <= 0) notFound();

  const { data, error } = await supabase
    .from("posts")
    .select("id, author_id, post_type, title, body, status, created_at")
    .eq("id", numericId)
    .is("deleted_at", null)
    .maybeSingle();
  if (error) throw error;
  if (!data) notFound();

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const { data: author, error: authorError } = await supabase
    .from("public_profiles")
    .select("nickname")
    .eq("id", data.author_id)
    .maybeSingle();
  if (authorError) throw authorError;

  return {
    id: String(data.id),
    postType: data.post_type as PostType,
    title: data.title,
    authorId: data.author_id,
    authorName: author?.nickname ?? "익명",
    publishedAt: formatPublishedDateKo(data.created_at),
    body: data.body,
    isAuthor: !!user && user.id === data.author_id,
  };
}
