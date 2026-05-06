"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { createClient } from "@/lib/supabase/client";

const schema = z.object({
  email: z.string().min(1, "아이디를 입력하세요"),
  password: z.string().min(1, "비밀번호를 입력하세요"),
  remember: z.boolean().optional(),
});

type FormValues = z.infer<typeof schema>;

export function LoginForm() {
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
      return;
    }
    window.location.href = "/idea";
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
            className="h-12 rounded-md border-zinc-300 px-4 text-sm placeholder:text-zinc-300"
          />
          <Input
            {...register("password")}
            type="password"
            placeholder="비밀번호"
            autoComplete="current-password"
            className="h-12 rounded-md border-zinc-300 px-4 text-sm placeholder:text-zinc-300"
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
        <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-zinc-500 select-none">
          <Checkbox
            checked={remember}
            onCheckedChange={(v) => setValue("remember", v === true)}
          />
          아이디 저장
        </label>
        <a
          href="/find-password"
          className="text-sm font-medium text-zinc-500 hover:text-zinc-700"
        >
          아이디/비밀번호 찾기
        </a>
      </div>

      {firstError && (
        <p className="mt-2 text-sm text-destructive">{firstError}</p>
      )}
    </form>
  );
}
