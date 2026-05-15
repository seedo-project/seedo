"use client";

import { LogIn, LogOut } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { CoinIcon } from "@/components/idea/idea-icons";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/auth-context";
import { toast } from "@/lib/toast";

const NAV_ITEMS = [
  { key: "idea", label: "아이디어", href: "/idea" },
  { key: "feed", label: "피드", href: "/feed" },
  { key: "board", label: "보드", href: "/board" },
  { key: "my-page", label: "마이페이지", href: "/my-page" },
] as const;

export function Navbar() {
  const pathname = usePathname() ?? "";
  const router = useRouter();
  const { user, logout } = useAuth();

  const currentKey = NAV_ITEMS.find((it) =>
    pathname === it.href || pathname.startsWith(`${it.href}/`),
  )?.key;

  const handleLogout = async () => {
    await logout();
    toast.success("로그아웃되었습니다");
    router.push("/login");
  };

  return (
    <header className="h-[108px] w-full border-b border-input px-4 md:px-[100px] py-[32px]">
      <div className="flex w-full items-start justify-between pt-5">
        <Link href="/idea" aria-label="Seedo 홈" className="block">
          <Image
            src="/seedo/logo-nav.svg"
            alt="Seedo"
            width={118}
            height={30}
            priority
            className="h-[29.591px] w-[118px]"
          />
        </Link>
        <div className="flex items-center gap-2 md:gap-8">
          <nav className="flex items-center gap-2 md:gap-10">
            {NAV_ITEMS.map(({ key, label, href }) => {
              const active = key === currentKey;
              return (
                <Link
                  key={key}
                  href={href}
                  className="relative flex items-center justify-center rounded-md px-1.5 py-2 md:px-3"
                >
                  <span
                    className={`text-xs leading-[1.5] font-bold tracking-[-0.375px] md:text-[15px] ${
                      active ? "text-foreground" : "text-muted-foreground"
                    }`}
                  >
                    {label}
                  </span>
                  {active && (
                    <span
                      aria-hidden
                      className="absolute -bottom-0.5 size-1 rounded-full bg-primary"
                    />
                  )}
                </Link>
              );
            })}
          </nav>
          {user ? (
            <div className="flex items-center gap-2">
              <Link
                href="/credits"
                aria-label="크레딧 잔액 / 거래 내역"
                className="flex items-center gap-2 rounded-full border border-input bg-background px-3 py-1.5 transition-colors hover:bg-muted"
              >
                <span className="text-[13px] font-bold text-foreground">
                  {user.displayName}
                </span>
                <span className="h-3 w-px bg-input" aria-hidden />
                <span className="flex items-center gap-1 text-[13px] font-medium text-muted-foreground">
                  <CoinIcon className="size-3.5 text-yellow-400" aria-hidden />
                  {user.creditBalance.toLocaleString()}
                </span>
              </Link>
              <Button
                variant="ghost"
                size="icon"
                aria-label="로그아웃"
                onClick={handleLogout}
              >
                <LogOut className="size-4" />
              </Button>
            </div>
          ) : (
            <Link
              href="/login"
              className="inline-flex h-7 items-center gap-1 rounded-[min(var(--radius-md),12px)] border border-border bg-background px-2.5 text-[0.8rem] font-medium text-foreground hover:bg-muted"
            >
              <LogIn className="size-3.5" aria-hidden />
              로그인
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
