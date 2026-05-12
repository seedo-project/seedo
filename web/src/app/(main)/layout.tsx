import { Navbar } from "@/components/shared/navbar";
import { AuthProvider, type AuthUser } from "@/contexts/auth-context";
import { resolveDisplayName } from "@/lib/auth-display";
import { createClient } from "@/lib/supabase/server";

async function fetchInitialUser(): Promise<AuthUser | null> {
  const supabase = await createClient();
  const {
    data: { user: authUser },
  } = await supabase.auth.getUser();
  if (!authUser) return null;

  const [{ data: profile }, { data: credits }] = await Promise.all([
    supabase
      .from("users")
      .select("id, nickname, email")
      .eq("id", authUser.id)
      .maybeSingle(),
    supabase
      .from("user_credits")
      .select("balance")
      .eq("user_id", authUser.id)
      .maybeSingle(),
  ]);

  if (!profile) return null;
  const metaName = (authUser.user_metadata ?? {})["name"];
  return {
    id: profile.id,
    nickname: profile.nickname,
    displayName: resolveDisplayName(metaName, profile.email, profile.nickname),
    email: profile.email,
    creditBalance: Number(credits?.balance ?? 0),
  };
}

export default async function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const initialUser = await fetchInitialUser();
  return (
    <AuthProvider initialUser={initialUser}>
      <Navbar />
      {children}
    </AuthProvider>
  );
}
