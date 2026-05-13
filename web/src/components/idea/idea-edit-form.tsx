"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import { useForm } from "react-hook-form";
import remarkGfm from "remark-gfm";
import { z } from "zod";

import { toast } from "@/lib/toast";
import type { ApiResponse } from "@/types/chat";
import type {
  PublishIdeaVersionRequest,
  PublishIdeaVersionResponse,
} from "@/types/idea";

const schema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "제목을 입력해 주세요.")
    .max(200, "제목은 200자 이하로 입력해 주세요."),
  contentMd: z.string().trim().min(1, "본문을 입력해 주세요."),
});

type FormValues = z.infer<typeof schema>;

class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

function showHttpError(err: unknown, fallback: string) {
  if (err instanceof HttpError) {
    if (err.status === 403) {
      toast.error("본인 아이디어만 수정할 수 있습니다");
      return;
    }
    if (err.status === 409) {
      toast.error("현재 상태에서는 새 버전을 발행할 수 없습니다");
      return;
    }
    if (err.status === 400) {
      toast.error(err.message);
      return;
    }
    if (err.status === 404) {
      toast.error("아이디어를 찾을 수 없습니다");
      return;
    }
    if (err.status >= 500) {
      toast.error(fallback);
      return;
    }
    toast.error(err.message);
    return;
  }
  toast.error(fallback);
}

export function IdeaEditForm({
  ideaId,
  initialTitle,
  initialContent,
}: {
  ideaId: string;
  initialTitle: string;
  initialContent: string;
}) {
  const router = useRouter();
  const [preview, setPreview] = useState(false);
  const {
    register,
    handleSubmit,
    watch,
    formState: { isValid, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: { title: initialTitle, contentMd: initialContent },
  });

  const contentMd = watch("contentMd");

  const onSubmit = async (values: FormValues) => {
    try {
      const payload: PublishIdeaVersionRequest = {
        title: values.title.trim(),
        contentMd: values.contentMd.trim(),
      };
      const res = await fetch(`/api/ideas/${ideaId}/versions`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const body = (await res
        .json()
        .catch(() => null)) as ApiResponse<PublishIdeaVersionResponse> | null;
      if (!res.ok || !body || body.status !== "OK") {
        throw new HttpError(
          res.status,
          body?.message ?? "새 버전 발행에 실패했습니다",
        );
      }
      toast.success(`v${body.data.version} 으로 발행되었습니다`);
      router.push(`/idea/${ideaId}`);
      router.refresh();
    } catch (err) {
      showHttpError(err, "새 버전 발행에 실패했습니다");
    }
  };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
        <header className="flex items-center justify-between">
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
            아이디어 수정
          </h1>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPreview((v) => !v)}
              className="flex h-9 items-center justify-center rounded-md bg-[#e4e4e7] px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-muted-foreground hover:bg-[#d4d4d8]"
            >
              {preview ? "편집으로" : "미리보기"}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              disabled={isSubmitting}
              className="flex h-9 items-center justify-center rounded-md border border-input bg-transparent px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-muted-foreground hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={!isValid || isSubmitting}
              className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? "발행 중..." : "새 버전 발행"}
            </button>
          </div>
        </header>
        <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
          이전 버전은 구매자 보호를 위해 보존됩니다. 새로 열람하는 사용자는 새
          버전을 보게 됩니다.
        </p>

        <div className="flex flex-col gap-2">
          <label
            htmlFor="idea-title"
            className="text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-foreground"
          >
            제목
          </label>
          <input
            id="idea-title"
            {...register("title")}
            type="text"
            placeholder="아이디어 제목"
            className="h-10 w-full rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
        </div>

        <div className="flex flex-col gap-2">
          <label
            htmlFor="idea-content"
            className="text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-foreground"
          >
            본문 (마크다운)
          </label>
          {preview ? (
            <article
              id="idea-content-preview"
              className="min-h-[400px] w-full rounded-md border border-input bg-card p-4 text-base leading-[1.5] tracking-[-0.4px] text-foreground"
            >
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {contentMd}
              </ReactMarkdown>
            </article>
          ) : (
            <textarea
              id="idea-content"
              {...register("contentMd")}
              placeholder="마크다운으로 본문을 작성하세요."
              className="h-[400px] w-full resize-none rounded-md border border-input bg-card p-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
          )}
        </div>
      </form>
    </main>
  );
}
