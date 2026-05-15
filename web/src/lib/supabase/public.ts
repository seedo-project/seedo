import { createClient } from "@supabase/supabase-js";

/**
 * 인증 컨텍스트가 필요 없는 공개 RLS SELECT 전용 클라이언트.
 *
 * `unstable_cache` 안에서 호출할 때 cookies() 의존을 제거해 캐시 무효화 키를
 * 단순화한다. anon 키 + persistSession=false 로 세션 부수효과 없음.
 */
export function createPublicClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey) {
    throw new Error(
      "Missing Supabase env vars: NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY",
    );
  }
  return createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}
