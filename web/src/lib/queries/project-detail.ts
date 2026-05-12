import { notFound } from "next/navigation";

import { createClient } from "@/lib/supabase/server";
import { formatPublishedDateKo } from "@/lib/format";
import type { ChipVariant } from "@/components/project/chip-status";

export type ProjectDetail = {
  id: string;
  status: ChipVariant;
  title: string;
  authorName: string;
  description: string;
  registeredAt: string;
  body: string;
  bookmarkCount: number;
  hypeCount: number;
};

function statusToChip(status: string): ChipVariant {
  switch (status) {
    case "IN_PROGRESS":
      return "in-progress";
    case "COMPLETED":
    case "ARCHIVED":
      return "completed";
    default:
      return "verifying";
  }
}

function snapshotTitle(md: string): string {
  const first = md.split("\n").find((l) => l.trim().startsWith("#"));
  if (first) return first.replace(/^#+\s*/, "").trim();
  return md.split("\n")[0]?.slice(0, 60) ?? "프로젝트";
}

function snapshotDescription(md: string): string {
  const lines = md
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));
  return lines.slice(0, 4).join(" ").slice(0, 220);
}

export async function fetchProjectDetail(id: string): Promise<ProjectDetail> {
  const supabase = await createClient();
  const numericId = Number(id);
  if (!Number.isFinite(numericId)) notFound();

  const { data } = await supabase
    .from("projects")
    .select(
      `id,
       status,
       leader_id,
       idea_snapshot_md,
       created_at,
       ideas:ideas!projects_idea_id_fkey (
         current_version_id,
         idea_documents!ideas_current_version_id_fkey ( title )
       )`,
    )
    .eq("id", numericId)
    .neq("status", "DELETED")
    .is("deleted_at", null)
    .maybeSingle();

  if (!data) notFound();

  const { data: leader } = await supabase
    .from("public_profiles")
    .select("nickname")
    .eq("id", data.leader_id)
    .maybeSingle();

  const idea = Array.isArray(data.ideas) ? data.ideas[0] : data.ideas;
  const doc = idea
    ? Array.isArray(idea.idea_documents)
      ? idea.idea_documents[0]
      : idea.idea_documents
    : null;
  const title = doc?.title ?? snapshotTitle(data.idea_snapshot_md);

  return {
    id: String(data.id),
    status: statusToChip(data.status),
    title,
    authorName: leader?.nickname ?? "익명",
    description: snapshotDescription(data.idea_snapshot_md),
    registeredAt: formatPublishedDateKo(data.created_at).replace(
      "게시",
      "등록",
    ),
    body: data.idea_snapshot_md,
    // TODO: hypes / project_follows 테이블 추가 후 실제 카운트로 교체
    bookmarkCount: 0,
    hypeCount: 0,
  };
}
