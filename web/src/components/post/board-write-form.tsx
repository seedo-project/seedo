"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";

import { POST_TYPES, type PostType } from "@/components/post/board-view";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import { useAuth } from "@/contexts/auth-context";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

const postTypeValues = POST_TYPES.map((t) => t.value) as [
  PostType,
  ...PostType[],
];

const schema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "제목을 입력해 주세요.")
    .max(200, "제목은 200자 이하여야 합니다."),
  postType: z.enum(postTypeValues),
  body: z
    .string()
    .trim()
    .min(1, "내용을 입력해 주세요.")
    .max(20000, "내용은 20000자 이하여야 합니다."),
});

type FormValues = z.infer<typeof schema>;

export function BoardWriteForm() {
  const router = useRouter();
  const { user } = useAuth();
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

  const onPublish = async (values: FormValues) => {
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }
    const supabase = createClient();
    const { data, error } = await supabase
      .from("posts")
      .insert({
        author_id: user.id,
        post_type: values.postType,
        title: values.title.trim(),
        body: values.body.trim(),
      })
      .select("id")
      .single();
    if (error || !data) {
      toast.error("게시물 등록에 실패했습니다");
      return;
    }
    toast.success("게시물을 등록했습니다");
    router.push(`/board/${data.id}`);
    router.refresh();
  };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <form onSubmit={handleSubmit(onPublish)}>
        <header className="flex w-full items-center justify-between">
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
            게시물 게시하기?
          </h1>
          <button
            type="submit"
            disabled={!isValid || isSubmitting}
            className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            게시하기
          </button>
        </header>

        <div className="mt-8 flex w-full flex-col gap-3">
          <div className="flex w-full items-start gap-2">
            <input
              type="text"
              maxLength={200}
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
            maxLength={20000}
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
