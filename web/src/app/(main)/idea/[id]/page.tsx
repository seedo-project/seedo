import { fetchIdeaDetail } from "@/lib/queries/idea-detail";

export default async function IdeaDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const idea = await fetchIdeaDetail(id);

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-4">
        <header className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-4">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              {idea.title}
            </h1>
            <button
              type="button"
              disabled
              aria-disabled="true"
              title="프로젝트 시작 — 채택 트랜잭션 별도 작업"
              className="flex h-9 cursor-not-allowed items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] whitespace-nowrap text-primary-foreground opacity-50"
            >
              프로젝트 시작하기
            </button>
          </div>
          <div className="flex items-center gap-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            <span>{idea.authorName}</span>
            <span aria-hidden className="h-2 w-px bg-border" />
            <span>{idea.publishedAt}</span>
          </div>
        </header>

        <article className="h-[776px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {idea.body}
        </article>
      </div>
    </main>
  );
}
