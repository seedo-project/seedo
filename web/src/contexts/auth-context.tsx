"use client";

import { createContext, useCallback, useContext, useMemo, useState } from "react";

export type AuthUser = {
  id: string;
  nickname: string;
  email: string;
  creditBalance: number;
};

type AuthContextValue = {
  user: AuthUser | null;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

// Supabase 연결 전까지의 임시 사용자.
// 잔액/닉네임은 my-page mock과 동일한 톤으로 맞춰 둠.
const MOCK_USER: AuthUser = {
  id: "00000000-0000-0000-0000-000000000001",
  nickname: "박소은",
  email: "soeun_park@korea.ac.kr",
  creditBalance: 320,
};

function initialUser(): AuthUser | null {
  if (
    process.env.NODE_ENV === "development" &&
    process.env.NEXT_PUBLIC_DEV_SKIP_AUTH === "true"
  ) {
    return MOCK_USER;
  }
  return null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => initialUser());

  const logout = useCallback(() => {
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({ user, logout }), [user, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within <AuthProvider>");
  }
  return ctx;
}
