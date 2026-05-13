"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Plus } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { useAuth } from "@/contexts/auth-context";
import type { DraftProject } from "@/lib/queries/projects";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";
import type { ApiResponse } from "@/types/chat";
import type {
  ProjectIntroResponse,
  UpdateProjectIntroRequest,
} from "@/types/project";

const COVER_BUCKET = "projects";

const schema = z.object({
  title: z.string().trim().min(1, "제목을 입력해 주세요."),
  description: z.string().trim().min(1, "설명을 입력해 주세요."),
  guide: z.string().trim().min(1, "가이드를 입력해 주세요."),
  coverImageUrl: z
    .union([
      z
        .string()
        .url("이미지 업로드가 완료되지 않았습니다.")
        .max(500, "이미지 URL이 너무 깁니다."),
      z.literal(""),
    ])
    .optional(),
});

type FormValues = z.infer<typeof schema>;

function pickPayloadDiff(
  values: FormValues,
  draft: DraftProject,
): UpdateProjectIntroRequest {
  const payload: UpdateProjectIntroRequest = {};
  const title = values.title.trim();
  const description = values.description.trim();
  const guideMd = values.guide.trim();
  const coverImageUrl = values.coverImageUrl?.trim() ?? "";

  if (title && title !== draft.title) payload.title = title;
  if (description && description !== draft.description) {
    payload.description = description;
  }
  if (guideMd && guideMd !== draft.guideMd) payload.guideMd = guideMd;
  if (coverImageUrl && coverImageUrl !== draft.coverImageUrl) {
    payload.coverImageUrl = coverImageUrl;
  }
  return payload;
}

async function patchIntro(
  projectId: string,
  payload: UpdateProjectIntroRequest,
): Promise<ProjectIntroResponse> {
  const res = await fetch(`/api/projects/${projectId}/intro`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = (await res
    .json()
    .catch(() => null)) as ApiResponse<ProjectIntroResponse> | null;
  if (!res.ok || !body || body.status !== "OK") {
    throw new HttpError(res.status, body?.message ?? "수정에 실패했습니다");
  }
  return body.data;
}

async function postPublish(projectId: string): Promise<ProjectIntroResponse> {
  const res = await fetch(`/api/projects/${projectId}/publish`, {
    method: "POST",
  });
  const body = (await res
    .json()
    .catch(() => null)) as ApiResponse<ProjectIntroResponse> | null;
  if (!res.ok || !body || body.status !== "OK") {
    throw new HttpError(res.status, body?.message ?? "공개에 실패했습니다");
  }
  return body.data;
}

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
      toast.error("프로젝트 LEADER만 수정/공개할 수 있습니다");
      return;
    }
    if (err.status === 409) {
      toast.error("현재 상태에서 진행할 수 없습니다");
      return;
    }
    if (err.status === 400) {
      toast.error(err.message);
      return;
    }
    toast.error(err.message);
    return;
  }
  toast.error(fallback);
}

export function ProjectWriteForm({ draft }: { draft: DraftProject }) {
  const router = useRouter();
  const { user } = useAuth();
  const [draftSnapshot, setDraftSnapshot] = useState(draft);
  const [savingDraft, setSavingDraft] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const {
    register,
    handleSubmit,
    getValues,
    setValue,
    watch,
    formState: { isValid, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: {
      title: draft.title,
      description: draft.description,
      guide: draft.guideMd,
      coverImageUrl: draft.coverImageUrl ?? "",
    },
  });

  const coverImageUrl = watch("coverImageUrl");

  const onUploadClick = () => {
    if (uploading || savingDraft || isSubmitting) return;
    fileInputRef.current?.click();
  };

  const onFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }
    if (!file.type.startsWith("image/")) {
      toast.error("이미지 파일만 업로드할 수 있습니다");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.error("이미지는 5MB 이하만 업로드할 수 있습니다");
      return;
    }

    setUploading(true);
    try {
      const supabase = createClient();
      const ext = file.name.split(".").pop() ?? "png";
      const path = `${user.id}/${draftSnapshot.id}-${Date.now()}.${ext}`;
      const { error: upErr } = await supabase.storage
        .from(COVER_BUCKET)
        .upload(path, file, { upsert: true, contentType: file.type });
      if (upErr) throw upErr;
      const { data } = supabase.storage.from(COVER_BUCKET).getPublicUrl(path);
      setValue("coverImageUrl", data.publicUrl, {
        shouldValidate: true,
        shouldDirty: true,
      });
    } catch (err) {
      console.error("cover upload failed", err);
      toast.error("이미지 업로드에 실패했습니다");
    } finally {
      setUploading(false);
    }
  };

  const handleDraft = async () => {
    if (savingDraft || isSubmitting) return;
    const values = getValues();
    const payload = pickPayloadDiff(values, draftSnapshot);
    if (Object.keys(payload).length === 0) {
      toast.info("변경된 내용이 없습니다");
      return;
    }
    setSavingDraft(true);
    try {
      const updated = await patchIntro(draftSnapshot.id, payload);
      setDraftSnapshot({
        id: String(updated.projectId),
        coverImageUrl: updated.coverImageUrl,
        title: updated.title ?? "",
        description: updated.description ?? "",
        guideMd: updated.guideMd ?? "",
      });
      toast.success("임시 저장 완료");
    } catch (err) {
      showHttpError(err, "임시 저장에 실패했습니다");
    } finally {
      setSavingDraft(false);
    }
  };

  const onPublish = async (values: FormValues) => {
    try {
      const payload = pickPayloadDiff(values, draftSnapshot);
      if (Object.keys(payload).length > 0) {
        await patchIntro(draftSnapshot.id, payload);
      }
      const published = await postPublish(draftSnapshot.id);
      toast.success("프로젝트가 공개되었습니다");
      router.push(`/feed/${published.projectId}`);
      router.refresh();
    } catch (err) {
      showHttpError(err, "공개에 실패했습니다");
    }
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
                disabled={savingDraft || isSubmitting}
                className="flex h-9 items-center justify-center rounded-md bg-[#e4e4e7] px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-muted-foreground hover:bg-[#d4d4d8] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {savingDraft ? "저장 중..." : "임시 저장"}
              </button>
              <button
                type="submit"
                disabled={!isValid || isSubmitting || savingDraft}
                className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isSubmitting ? "게시 중..." : "프로젝트 게시"}
              </button>
            </div>
          </header>
          <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            새 프로젝트를 시작하고 함께할 동료를 모아보세요.
          </p>
        </div>

        <div className="mt-8 flex w-full flex-col gap-5">
          <FormField label="프로젝트 대표 이미지">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={onFileChange}
            />
            <button
              type="button"
              onClick={onUploadClick}
              aria-label="이미지 업로드"
              disabled={uploading}
              className="relative flex size-32 items-center justify-center overflow-hidden rounded-lg border border-input bg-muted text-muted-foreground hover:bg-[#e4e4e7] disabled:cursor-not-allowed"
            >
              {coverImageUrl ? (
                <Image
                  src={coverImageUrl}
                  alt="프로젝트 대표 이미지 미리보기"
                  fill
                  sizes="128px"
                  className="object-cover"
                  unoptimized
                />
              ) : uploading ? (
                <Loader2 className="size-6 animate-spin" aria-hidden />
              ) : (
                <Plus className="size-6" aria-hidden />
              )}
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
