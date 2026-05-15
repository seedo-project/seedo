import Link from "next/link";

import { EmptyState } from "@/components/shared/empty-state";
import { ProjectWriteForm } from "@/components/project/project-write-form";
import { fetchLatestDraftProject } from "@/lib/queries/projects";

export default async function ProjectStartPage() {
  const draft = await fetchLatestDraftProject();

  if (!draft) {
    return (
      <main className="mx-auto w-full max-w-[820px] px-4 pt-8 pb-16 md:px-0">
        <EmptyState
          title="아직 시작할 프로젝트가 없어요"
          description="아이디어 상세에서 '프로젝트 시작하기' 로 채택하면 DRAFT 프로젝트가 생성됩니다"
          action={
            <Link
              href="/idea"
              className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
            >
              아이디어 둘러보기
            </Link>
          }
        />
      </main>
    );
  }

  return <ProjectWriteForm draft={draft} />;
}
