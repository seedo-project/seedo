import { IdeaCard, type Idea } from "@/components/idea/idea-card";

export function IdeasPanel({ ideas }: { ideas: Idea[] }) {
  if (ideas.length === 0) {
    return (
      <div className="flex h-40 items-center justify-center rounded-xl border border-border text-sm text-muted-foreground">
        아직 작성한 아이디어가 없습니다.
      </div>
    );
  }
  return (
    <section className="grid grid-cols-2 gap-5">
      {ideas.map((i) => (
        <IdeaCard key={i.id} idea={i} />
      ))}
    </section>
  );
}
