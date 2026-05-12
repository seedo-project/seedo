"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

const schema = z.object({
  email: z.string().min(1, "아이디를 입력하세요"),
  password: z.string().min(1, "비밀번호를 입력하세요"),
  remember: z.boolean().optional(),
});

type FormValues = z.infer<typeof schema>;

export function LoginForm() {
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { isSubmitting, errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "", remember: false },
  });

  const onSubmit = async (values: FormValues) => {
    setError(null);
    const supabase = createClient();
    const { error: signInError } = await supabase.auth.signInWithPassword({
      email: values.email,
      password: values.password,
    });
    if (signInError) {
      setError(signInError.message);
      toast.error("로그인에 실패했습니다");
      return;
    }
    toast.success("환영합니다");
    // proxy 가 미인증 진입 시 ?redirect=원래경로 를 붙여줌. 안전한 내부 경로만 허용.
    const redirectParam = searchParams?.get("redirect") ?? "";
    const target = redirectParam.startsWith("/") && !redirectParam.startsWith("//")
      ? redirectParam
      : "/idea";
    // 서버 cookies 동기화 위해 hard navigation.
    window.location.href = target;
  };

  const remember = watch("remember") ?? false;
  const firstError = errors.email?.message ?? errors.password?.message ?? error;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="w-[400px]">
      <div className="flex gap-3">
        <div className="flex flex-1 flex-col gap-3">
          <Input
            {...register("email")}
            placeholder="아이디"
            autoComplete="username"
            className="h-12 rounded-md border-input px-4 text-sm placeholder:text-muted-foreground"
          />
          <Input
            {...register("password")}
            type="password"
            placeholder="비밀번호"
            autoComplete="current-password"
            className="h-12 rounded-md border-input px-4 text-sm placeholder:text-muted-foreground"
          />
        </div>
        <Button
          type="submit"
          disabled={isSubmitting}
          className="h-auto self-stretch rounded-md bg-primary px-9 text-sm leading-tight font-semibold text-primary-foreground hover:bg-primary/90"
        >
          로그인
        </Button>
      </div>

      <div className="mt-3 flex items-center justify-between">
        <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-muted-foreground select-none">
          <Checkbox
            checked={remember}
            onCheckedChange={(v) => setValue("remember", v === true)}
          />
          아이디 저장
        </label>
        <a
          href="/find-password"
          className="text-sm font-medium text-muted-foreground hover:text-foreground"
        >
          비밀번호 찾기
        </a>
      </div>

      {firstError && (
        <p className="mt-2 text-sm text-destructive">{firstError}</p>
      )}
    </form>
  );
}
