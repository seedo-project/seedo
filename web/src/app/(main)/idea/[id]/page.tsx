import { AdoptButton } from "@/components/idea/adopt-button";
import { fetchIdeaDetail } from "@/lib/queries/idea-detail";

export default async function IdeaDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const idea = await fetchIdeaDetail(id);
  const canAdopt = idea.isAuthor || idea.isPurchased;

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-4">
        <header className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-4">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              {idea.title}
            </h1>
            <AdoptButton
              ideaId={idea.id}
              canAdopt={canAdopt}
              rewardCredits={idea.rewardCredits}
            />
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
