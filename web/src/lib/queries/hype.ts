import { createClient } from "@/lib/supabase/server";

export type HypeState = { count: number; hyped: boolean };

export async function fetchHypeState(
  target: "idea" | "project",
  targetId: number,
): Promise<HypeState> {
  const supabase = await createClient();
  const col = target === "idea" ? "idea_id" : "project_id";

  const {
    data: { user },
  } = await supabase.auth.getUser();

  const [countResult, mineResult] = await Promise.all([
    supabase
      .from("hypes")
      .select("id", { count: "exact", head: true })
      .eq(col, targetId),
    user
      ? supabase
          .from("hypes")
          .select("id")
          .eq("user_id", user.id)
          .eq(col, targetId)
          .maybeSingle()
      : Promise.resolve({ data: null, error: null }),
  ]);

  if (countResult.error) throw countResult.error;
  if (mineResult.error) throw mineResult.error;

  return {
    count: countResult.count ?? 0,
    hyped: !!mineResult.data,
  };
}
