"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";

import { createClient } from "@/lib/supabase/client";

type Status = "idle" | "sent" | "error";

export default function FindPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [status, setStatus] = useState<Status>("idle");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isValidEmail || submitting) return;
    setSubmitting(true);
    setErrorMsg(null);
    try {
      const supabase = createClient();
      const { error } = await supabase.auth.resetPasswordForEmail(email);
      if (error) {
        setErrorMsg(error.message);
        setStatus("error");
        return;
      }
      setStatus("sent");
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "알 수 없는 오류");
      setStatus("error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted px-4 py-12">
      <div className="flex w-[440px] flex-col items-center gap-5 rounded-xl bg-card px-5 pb-5">
        <header className="flex w-full items-center justify-between py-5">
          <button
            type="button"
            onClick={() => router.push("/login")}
            aria-label="로그인으로"
            className="flex size-7 items-center px-[5.833px] py-[4.667px]"
          >
            <Image
              src="/seedo/back-arrow.svg"
              alt=""
              width={10}
              height={18}
              className="h-[18.249px] w-[10px]"
            />
          </button>
          <h1 className="text-xl font-bold tracking-[-0.5px] text-foreground">
            비밀번호 찾기
          </h1>
          <div className="size-7" aria-hidden />
        </header>

        <form
          onSubmit={handleSubmit}
          className="flex w-[400px] flex-col items-start"
        >
          <p className="px-3 text-sm leading-[1.5] tracking-[-0.35px] text-muted-foreground">
            가입하신 이메일로 비밀번호 재설정 링크를 보내드립니다.
          </p>

          <div className="flex w-full items-center justify-between py-5">
            <label
              htmlFor="email"
              className="text-base font-semibold leading-[1.5] tracking-[-0.4px] text-foreground"
            >
              아이디(이메일)
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일"
              autoComplete="email"
              className="h-12 w-[276px] rounded-md border border-input px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:font-normal placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/30 focus:outline-none"
            />
          </div>

          {status === "sent" && (
            <p
              aria-live="polite"
              className="w-full pb-2 text-center text-sm text-muted-foreground"
            >
              이메일로 비밀번호 재설정 링크를 보냈습니다.
            </p>
          )}
          {status === "error" && errorMsg && (
            <p
              role="alert"
              aria-live="assertive"
              className="w-full pb-2 text-center text-sm text-destructive"
            >
              {errorMsg}
            </p>
          )}

          <button
            type="submit"
            disabled={!isValidEmail || submitting}
            className={`flex h-12 items-center justify-center self-center rounded-md px-10 py-3 text-sm leading-[1.3] font-semibold tracking-[-0.35px] transition-colors ${
              !isValidEmail || submitting
                ? "bg-[#e4e4e7] text-[#a1a1aa]"
                : "bg-foreground text-background hover:bg-[#3f3f46]"
            }`}
          >
            비밀번호 찾기
          </button>
        </form>
      </div>
    </main>
  );
}
