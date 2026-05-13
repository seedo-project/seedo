import { createClient } from "@/lib/supabase/server";

export type PostApplicant = {
  id: number;
  applicantId: string;
  applicantName: string;
  message: string | null;
  appliedAt: string;
};

export async function fetchPostApplicants(
  postId: number,
): Promise<PostApplicant[]> {
  const supabase = await createClient();

  const { data: rows, error: rowsError } = await supabase
    .from("post_applications")
    .select("id, applicant_id, message, applied_at")
    .eq("post_id", postId)
    .order("applied_at", { ascending: false });
  if (rowsError) throw rowsError;

  const applications = rows ?? [];
  if (applications.length === 0) return [];

  const applicantIds = [...new Set(applications.map((a) => a.applicant_id))];
  const { data: profiles, error: profilesError } = await supabase
    .from("public_profiles")
    .select("id, nickname")
    .in("id", applicantIds);
  if (profilesError) throw profilesError;

  const nameById = new Map(
    (profiles ?? []).map((p) => [p.id, p.nickname ?? "익명"]),
  );

  return applications.map((a) => ({
    id: Number(a.id),
    applicantId: a.applicant_id,
    applicantName: nameById.get(a.applicant_id) ?? "익명",
    message: a.message,
    appliedAt: a.applied_at,
  }));
}
