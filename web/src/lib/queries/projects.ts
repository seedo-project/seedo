import { createClient } from "@/lib/supabase/server";
import type { Project } from "@/components/project/project-card";
import type { ChipVariant } from "@/components/project/chip-status";

export type DraftProject = {
  id: string;
  coverImageUrl: string | null;
  title: string;
  description: string;
  guideMd: string;
};

/** 본인의 가장 최근 DRAFT 프로젝트 1건. 아이디어 채택 (§8.3) 으로만 생성되므로 없는 게 정상. */
export async function fetchLatestDraftProject(): Promise<DraftProject | null> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return null;

  const { data } = await supabase
    .from("projects")
    .select("id, cover_image_url, title, description, guide_md")
    .eq("leader_id", user.id)
    .eq("status", "DRAFT")
    .is("deleted_at", null)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (!data) return null;
  return {
    id: String(data.id),
    coverImageUrl: data.cover_image_url ?? null,
    title: data.title ?? "",
    description: data.description ?? "",
    guideMd: data.guide_md ?? "",
  };
}

function statusToChips(status: string): ChipVariant[] {
  switch (status) {
    case "IN_PROGRESS":
      return ["in-progress"];
    case "DRAFT":
    case "RECRUITING":
      return ["verifying"];
    case "COMPLETED":
    case "ARCHIVED":
      return ["completed"];
    default:
      return [];
  }
}

function snapshotPreview(md: string): string {
  const lines = md
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));
  return lines.slice(0, 3).join(" ").slice(0, 200);
}

function snapshotTitle(md: string): string {
  // 첫 # 헤딩이 있으면 제목으로. 없으면 첫 줄 줄여서.
  const first = md.split("\n").find((l) => l.trim().startsWith("#"));
  if (first) return first.replace(/^#+\s*/, "").trim();
  return md.split("\n")[0]?.slice(0, 40) ?? "프로젝트";
}

export async function fetchProjectFeed(): Promise<Project[]> {
  const supabase = await createClient();

  const { data, error } = await supabase
    .from("projects")
    .select(
      `id,
       status,
       leader_id,
       title,
       description,
       cover_image_url,
       idea_snapshot_md,
       ideas:ideas!projects_idea_id_fkey (
         current_version_id,
         idea_documents!ideas_current_version_id_fkey ( title )
       )`,
    )
    .neq("status", "DELETED")
    .is("deleted_at", null)
    .order("created_at", { ascending: false });

  if (error) throw error;
  if (!data) return [];

  // public_profiles 는 view 라 embed 불가 — 별도 조회 후 join.
  const leaderIds = Array.from(new Set(data.map((r) => r.leader_id)));
  const { data: profiles } = await supabase
    .from("public_profiles")
    .select("id, nickname")
    .in("id", leaderIds);
  const profileMap = new Map<string, string>(
    (profiles ?? []).map((p) => [p.id, p.nickname]),
  );

  return data.map((row): Project => {
    const idea = Array.isArray(row.ideas) ? row.ideas[0] : row.ideas;
    const doc = idea
      ? Array.isArray(idea.idea_documents)
        ? idea.idea_documents[0]
        : idea.idea_documents
      : null;
    // publish 시 NOT NULL 가드(V15)가 있지만 DRAFT 카드도 노출될 수 있어 idea 스냅샷 폴백 유지.
    const title = row.title ?? doc?.title ?? snapshotTitle(row.idea_snapshot_md);
    const description =
      row.description ?? snapshotPreview(row.idea_snapshot_md);
    return {
      id: String(row.id),
      title,
      subtitle: profileMap.get(row.leader_id) ?? "",
      description,
      thumbnailUrl: row.cover_image_url ?? null,
      statuses: statusToChips(row.status),
    };
  });
}
