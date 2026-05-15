import Image from "next/image";

import { ChipStatus } from "@/components/project/chip-status";
import { CommentSection } from "@/components/shared/comment-section";
import { HypeButton } from "@/components/shared/hype-button";
import { ProjectFollowButton } from "@/components/shared/project-follow-button";
import { ProjectScrapButton } from "@/components/shared/project-scrap-button";
import { fetchComments } from "@/lib/queries/comments";
import { fetchHypeState } from "@/lib/queries/hype";
import { fetchProjectDetail } from "@/lib/queries/project-detail";
import { fetchProjectFollowState } from "@/lib/queries/project-follow";
import { fetchProjectScrapState } from "@/lib/queries/project-scrap";

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const project = await fetchProjectDetail(id);
  const projectIdNum = Number(project.id);
  const [hype, scrap, follow, comments] = await Promise.all([
    fetchHypeState("project", projectIdNum),
    fetchProjectScrapState(projectIdNum),
    fetchProjectFollowState(projectIdNum),
    fetchComments("project", projectIdNum),
  ]);

  return (
    <main className="mx-auto w-full max-w-[820px] px-4 pt-8 pb-16 md:px-0">
      <div className="flex flex-col gap-8">
        <div className="flex w-full flex-col gap-6 md:flex-row md:gap-10">
          <div className="relative aspect-square w-full shrink-0 overflow-hidden rounded-lg bg-muted md:size-[295px]">
            {project.coverImageUrl && (
              <Image
                src={project.coverImageUrl}
                alt=""
                fill
                sizes="(max-width: 768px) 100vw, 295px"
                className="object-cover"
                preload
              />
            )}
          </div>
          <div className="flex flex-1 flex-col items-end justify-between">
            <div className="flex w-full flex-col gap-1.5">
              <div className="flex items-start justify-between gap-1.5">
                <ChipStatus variant={project.status} />
                <ProjectFollowButton
                  projectId={projectIdNum}
                  initialCount={follow.count}
                  initialFollowed={follow.followed}
                />
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
              <ProjectScrapButton
                projectId={projectIdNum}
                initialCount={scrap.count}
                initialScrapped={scrap.scrapped}
              />
              <HypeButton
                target={{ kind: "project", id: projectIdNum }}
                initialCount={hype.count}
                initialHyped={hype.hyped}
              />
            </div>
          </div>
        </div>

        <article className="h-[520px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {project.body}
        </article>

        {project.ideaSnapshotMd && (
          <details className="rounded-md border border-border">
            <summary className="cursor-pointer px-4 py-3 text-sm leading-[1.5] font-semibold tracking-[-0.35px] text-foreground">
              원본 아이디어 보기
            </summary>
            <div className="border-t border-border p-4 text-sm leading-[1.5] tracking-[-0.35px] whitespace-pre-line text-muted-foreground">
              {project.ideaSnapshotMd}
            </div>
          </details>
        )}

        <CommentSection
          target="project"
          targetId={projectIdNum}
          initialComments={comments}
        />
      </div>
    </main>
  );
}
