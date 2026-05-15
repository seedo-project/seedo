import Link from "next/link";

import { ProjectCard } from "@/components/project/project-card";
import { EmptyState } from "@/components/shared/empty-state";
import { fetchProjectFeed } from "@/lib/queries/projects";

export default async function FeedPage() {
  const projects = await fetchProjectFeed();

  return (
    <main className="px-[100px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between">
        <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
          지금 뜨는 프로젝트
        </h1>
        <Link
          href="/feed/start"
          className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
        >
          프로젝트 게시
        </Link>
      </div>

      {projects.length === 0 ? (
        <EmptyState
          title="아직 등록된 프로젝트가 없습니다"
          description="첫 프로젝트가 곧 올라옵니다"
        />
      ) : (
        <section className="mt-8 grid grid-cols-2 gap-4">
          {projects.map((p) => (
            <ProjectCard key={p.id} project={p} href={`/feed/${p.id}`} />
          ))}
        </section>
      )}
    </main>
  );
}
