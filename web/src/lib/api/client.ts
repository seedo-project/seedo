import { createClient } from "@/lib/supabase/server";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

export type SpringFetchInit = Omit<RequestInit, "headers"> & {
  headers?: Record<string, string>;
};

/**
 * Spring API 호출용 fetch wrapper.
 * Server Component / Route Handler에서 호출. Supabase JWT를 Authorization으로 자동 첨부.
 */
export async function springFetch(path: string, init: SpringFetchInit = {}) {
  if (!API_BASE) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured");
  }

  const supabase = await createClient();
  const {
    data: { session },
  } = await supabase.auth.getSession();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...init.headers,
  };
  if (session) {
    headers.Authorization = `Bearer ${session.access_token}`;
  }

  return fetch(`${API_BASE}${path}`, { ...init, headers });
}
