import { createClient } from "@/lib/supabase/server";

export type PostApplicationState = { applied: boolean };

export async function fetchPostApplicationState(
  postId: number,
): Promise<PostApplicationState> {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) return { applied: false };

  const { data, error } = await supabase
    .from("post_applications")
    .select("id")
    .eq("post_id", postId)
    .eq("applicant_id", user.id)
    .maybeSingle();
  if (error) throw error;

  return { applied: !!data };
}
