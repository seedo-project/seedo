import { createClient } from "@/lib/supabase/server";

export type ProjectFollowState = { count: number; followed: boolean };

export async function fetchProjectFollowState(
  projectId: number,
): Promise<ProjectFollowState> {
  const supabase = await createClient();

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const [countResult, mineResult] = await Promise.all([
    supabase
      .from("project_follows")
      .select("user_id", { count: "exact", head: true })
      .eq("project_id", projectId),
    user
      ? supabase
          .from("project_follows")
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
    followed: !!mineResult.data,
  };
}
