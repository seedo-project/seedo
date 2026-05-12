"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

import { resolveDisplayName } from "@/lib/auth-display";
import { createClient } from "@/lib/supabase/client";

export type AuthUser = {
  id: string;
  nickname: string;
  /** UI 표시용 이름. 가입 시 입력한 name → 이메일 prefix → nickname(UUID fallback) 순. */
  displayName: string;
  email: string;
  creditBalance: number;
};

type AuthContextValue = {
  user: AuthUser | null;
  loading: boolean;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({
  initialUser = null,
  children,
}: {
  initialUser?: AuthUser | null;
  children: React.ReactNode;
}) {
  const [user, setUser] = useState<AuthUser | null>(initialUser);
  const [loading, setLoading] = useState(initialUser === null);

  const fetchProfile = useCallback(async (): Promise<AuthUser | null> => {
    const supabase = createClient();
    const {
      data: { user: authUser },
    } = await supabase.auth.getUser();
    if (!authUser) return null;

    // RLS: users self_select / user_credits self_select 가 본인 row 만 반환.
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
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setUser(await fetchProfile());
    } finally {
      setLoading(false);
    }
  }, [fetchProfile]);

  useEffect(() => {
    const supabase = createClient();

    if (initialUser === null) {
      refresh();
    }

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      if (!session) {
        setUser(null);
        setLoading(false);
      } else {
        refresh();
      }
    });

    return () => subscription.unsubscribe();
  }, [initialUser, refresh]);

  const logout = useCallback(async () => {
    const supabase = createClient();
    await supabase.auth.signOut();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, logout, refresh }),
    [user, loading, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within <AuthProvider>");
  }
  return ctx;
}
