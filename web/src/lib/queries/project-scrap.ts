import { createClient } from "@/lib/supabase/server";

export type ProjectScrapState = { count: number; scrapped: boolean };

export async function fetchProjectScrapState(
  projectId: number,
): Promise<ProjectScrapState> {
  const supabase = await createClient();

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const [countResult, mineResult] = await Promise.all([
    supabase
      .from("project_scraps")
      .select("user_id", { count: "exact", head: true })
      .eq("project_id", projectId),
    user
      ? supabase
          .from("project_scraps")
          .select("user_id")
          .eq("user_id", user.id)
          .eq("project_id", projectId)
          .maybeSingle()
      : Promise.resolve({ data: null, error: null }),
  ]);

  if (countResult.error) throw countResult.error;
  if (mineResult.error) throw mineResult.error;

  return {
    count: countResult.count ?? 0,
    scrapped: !!mineResult.data,
  };
}
