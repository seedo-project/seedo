"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Plus } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";

const schema = z.object({
  title: z.string().trim().min(1, "제목을 입력해 주세요."),
  description: z.string().trim().min(1, "설명을 입력해 주세요."),
  guide: z.string().trim().min(1, "가이드를 입력해 주세요."),
});

type FormValues = z.infer<typeof schema>;

export function ProjectWriteForm() {
  const {
    register,
    handleSubmit,
    formState: { isValid, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: { title: "", description: "", guide: "" },
  });

  const handleDraft = () => {
    // TODO: Spring API 연결 — DRAFT 저장
  };

  const onPublish = (_values: FormValues) => {
    // TODO: Spring API 연결 — 발행 (§8.3 채택→프로젝트+보상)
  };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <form onSubmit={handleSubmit(onPublish)}>
        <div className="flex flex-col gap-1">
          <header className="flex w-full items-center justify-between">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              새 프로젝트 시작하기
            </h1>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleDraft}
                className="flex h-9 items-center justify-center rounded-md bg-[#e4e4e7] px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-muted-foreground hover:bg-[#d4d4d8]"
              >
                임시 저장
              </button>
              <button
                type="submit"
                disabled={!isValid || isSubmitting}
                className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                프로젝트 게시
              </button>
            </div>
          </header>
          <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            새 프로젝트를 시작하고 함께할 동료를 모아보세요.
          </p>
        </div>

        <div className="mt-8 flex w-full flex-col gap-5">
          <FormField label="프로젝트 대표 이미지">
            <button
              type="button"
              aria-label="이미지 업로드"
              className="flex size-32 items-center justify-center rounded-lg border border-input bg-muted text-muted-foreground hover:bg-[#e4e4e7]"
            >
              <Plus className="size-6" aria-hidden />
            </button>
          </FormField>

          <FormField label="프로젝트 제목">
            <input
              {...register("title")}
              type="text"
              placeholder="제목을 입력해 주세요."
              aria-label="프로젝트 제목"
              className="h-10 w-full rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
          </FormField>

          <FormField label="프로젝트 설명">
            <input
              {...register("description")}
              type="text"
              placeholder="설명을 입력해 주세요."
              aria-label="프로젝트 설명"
              className="h-10 w-full rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
          </FormField>

          <FormField label="프로젝트 가이드">
            <textarea
              {...register("guide")}
              placeholder="프로젝트 진행 가이드를 입력해 주세요."
              aria-label="프로젝트 가이드"
              className="h-[400px] w-full resize-none rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
          </FormField>
        </div>
      </form>
    </main>
  );
}

function FormField({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-foreground">
        {label}
      </p>
      {children}
    </div>
  );
}
