import { Bookmark, Star } from "lucide-react";

import { ChipStatus } from "@/components/project/chip-status";
import { fetchProjectDetail } from "@/lib/queries/project-detail";

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const project = await fetchProjectDetail(id);

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-8">
        <div className="flex w-full gap-10">
          <div
            className="size-[295px] shrink-0 rounded-lg bg-muted"
            aria-hidden
          />
          <div className="flex flex-1 flex-col items-end justify-between">
            <div className="flex w-full flex-col gap-1.5">
              <div className="flex items-start gap-1.5">
                <ChipStatus variant={project.status} />
              </div>
              <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
                {project.title}
              </h1>
              <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
                {project.authorName}
              </p>
              <p className="line-clamp-3 text-base leading-[1.5] font-medium tracking-[-0.4px] text-muted-foreground">
                {project.description}
              </p>
              <p className="text-xs leading-[1.5] font-medium tracking-[-0.3px] text-muted-foreground/70">
                {project.registeredAt}
              </p>
            </div>
            <div className="flex items-center">
              {/* TODO: 북마크/Hype 토글 — hypes / project_follows 테이블 추가 후 활성화. 현재는 카운트만 표시. */}
              <div
                role="group"
                aria-label={`북마크 ${project.bookmarkCount.toLocaleString("ko-KR")}회`}
                className="flex size-[60px] flex-col items-center justify-center text-muted-foreground"
              >
                <Bookmark className="size-6" aria-hidden />
                <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
                  {project.bookmarkCount.toLocaleString("ko-KR")}
                </span>
              </div>
              <div
                role="group"
                aria-label={`Hype ${project.hypeCount.toLocaleString("ko-KR")}회`}
                className="flex size-[60px] flex-col items-center justify-center text-muted-foreground"
              >
                <Star className="size-6" aria-hidden />
                <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
                  {project.hypeCount.toLocaleString("ko-KR")}
                </span>
              </div>
            </div>
          </div>
        </div>

        <article className="h-[520px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {project.body}
        </article>
      </div>
    </main>
  );
}
