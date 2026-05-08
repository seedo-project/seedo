"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";

import { POST_TYPES, type PostType } from "@/components/post/board-view";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";

const postTypeValues = POST_TYPES.map((t) => t.value) as [
  PostType,
  ...PostType[],
];

const schema = z.object({
  title: z.string().trim().min(1, "제목을 입력해 주세요."),
  postType: z.enum(postTypeValues),
  body: z.string().trim().min(1, "내용을 입력해 주세요."),
});

type FormValues = z.infer<typeof schema>;

export function BoardWriteForm() {
  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { isValid, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: { title: "", body: "" },
  });

  const postType = watch("postType");
  const selectedLabel =
    POST_TYPES.find((t) => t.value === postType)?.label ?? "";

  const handleDraft = () => {
    // TODO: Spring API 연결 — DRAFT 저장 (검증 무관, 작성 중인 값 그대로)
  };

  const onPublish = (_values: FormValues) => {
    // TODO: Spring API 연결 — 발행
  };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <form onSubmit={handleSubmit(onPublish)}>
        <header className="flex w-full items-center justify-between">
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
            게시물 게시하기?
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
              게시하기
            </button>
          </div>
        </header>

        <div className="mt-8 flex w-full flex-col gap-3">
          <div className="flex w-full items-start gap-2">
            <input
              type="text"
              placeholder="게시물 제목을 입력해 주세요."
              aria-label="게시물 제목"
              className="h-10 flex-1 rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
              {...register("title")}
            />
            <Controller
              control={control}
              name="postType"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger
                    aria-label="게시판 선택"
                    className="h-10 w-[190px] rounded-md border border-input bg-card px-3 text-sm leading-[1.5] tracking-[-0.35px] text-muted-foreground"
                  >
                    <span>{selectedLabel || "게시판 선택..."}</span>
                  </SelectTrigger>
                  <SelectContent>
                    {POST_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <textarea
            placeholder="내용을 입력해 주세요."
            aria-label="게시물 내용"
            className="h-[480px] w-full resize-none rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
            {...register("body")}
          />
        </div>
      </form>
    </main>
  );
}
