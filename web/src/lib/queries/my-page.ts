import { resolveDisplayName } from "@/lib/auth-display";
import { createClient } from "@/lib/supabase/server";
import { formatRelativeKo } from "@/lib/format";
import type { Idea } from "@/components/idea/idea-card";
import type { Project } from "@/components/project/project-card";
import type { ChipVariant } from "@/components/project/chip-status";
import type { ProfileMock } from "@/app/(main)/my-page/_components/profile-panel";

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

function genderFromMeta(g: unknown): ProfileMock["gender"] {
  if (g === "male") return "MALE";
  if (g === "female") return "FEMALE";
  return "UNDISCLOSED";
}

function snapshotTitle(md: string): string {
  const first = md.split("\n").find((l) => l.trim().startsWith("#"));
  if (first) return first.replace(/^#+\s*/, "").trim();
  return md.split("\n")[0]?.slice(0, 40) ?? "프로젝트";
}

function snapshotPreview(md: string): string {
  const lines = md
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));
  return lines.slice(0, 3).join(" ").slice(0, 200);
}

export type MyPageQueryResult = {
  profile: ProfileMock;
  ideas: Idea[];
  projects: Project[];
};

export async function fetchMyPageData(): Promise<MyPageQueryResult | null> {
  const supabase = await createClient();
  const {
    data: { user: authUser },
  } = await supabase.auth.getUser();
  if (!authUser) return null;

  const meta = (authUser.user_metadata ?? {}) as Record<string, unknown>;
  const birthDate = typeof meta.birth_date === "string" ? meta.birth_date : "";
  const [y, m, d] = birthDate.split("-");

  const profile: ProfileMock = {
    name: typeof meta.name === "string" ? meta.name : "",
    birthYear: y ?? "",
    birthMonth: m ?? "",
    birthDay: d ?? "",
    gender: genderFromMeta(meta.gender),
    email: authUser.email ?? "",
  };

  const [{ data: ideaRows }, { data: projectRows }, { data: ownProfile }] =
    await Promise.all([
      supabase
        .from("ideas")
        .select(
          `id, price_credits, created_at,
         idea_embeddings ( keywords )`,
        )
        .eq("author_id", authUser.id)
        .neq("status", "DELETED")
        .is("deleted_at", null)
        .order("created_at", { ascending: false }),
      supabase
        .from("projects")
        .select(
          `id, status, idea_snapshot_md,
         ideas:ideas!projects_idea_id_fkey (
           current_version_id,
           idea_documents!ideas_current_version_id_fkey ( title )
         )`,
        )
        .eq("leader_id", authUser.id)
        .neq("status", "DELETED")
        .is("deleted_at", null)
        .order("created_at", { ascending: false }),
      supabase
        .from("public_profiles")
        .select("nickname")
        .eq("id", authUser.id)
        .maybeSingle(),
    ]);
  const projectSubtitle = resolveDisplayName(
    profile.name,
    profile.email,
    ownProfile?.nickname ?? "",
  );

  const ideas: Idea[] = (ideaRows ?? []).map((row) => {
    const emb = Array.isArray(row.idea_embeddings)
      ? row.idea_embeddings[0]
      : row.idea_embeddings;
    return {
      id: String(row.id),
      variant: "default",
      tags: emb?.keywords ?? [],
      postedAt: formatRelativeKo(row.created_at),
      priceCredits: row.price_credits,
    };
  });

  const projects: Project[] = (projectRows ?? []).map((row) => {
    const idea = Array.isArray(row.ideas) ? row.ideas[0] : row.ideas;
    const doc = idea
      ? Array.isArray(idea.idea_documents)
        ? idea.idea_documents[0]
        : idea.idea_documents
      : null;
    return {
      id: String(row.id),
      title: doc?.title ?? snapshotTitle(row.idea_snapshot_md),
      subtitle: projectSubtitle,
      description: snapshotPreview(row.idea_snapshot_md),
      thumbnailUrl: null,
      statuses: statusToChips(row.status),
    };
  });

  return { profile, ideas, projects };
}
