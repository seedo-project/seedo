import { Lightbulb } from "lucide-react";

import { IdeaCard, type Idea } from "@/components/idea/idea-card";
import { EmptyState } from "@/components/shared/empty-state";

export function IdeasPanel({ ideas }: { ideas: Idea[] }) {
  if (ideas.length === 0) {
    return (
      <EmptyState
        icon={Lightbulb}
        title="아직 작성한 아이디어가 없습니다"
        description="첫 아이디어를 작성해보세요"
      />
    );
  }
  return (
    <section className="grid grid-cols-1 sm:grid-cols-2 gap-5">
      {ideas.map((i) => (
        <IdeaCard key={i.id} idea={i} />
      ))}
    </section>
  );
}
