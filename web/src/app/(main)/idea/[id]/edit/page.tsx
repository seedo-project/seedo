import { notFound } from "next/navigation";

import { IdeaEditForm } from "@/components/idea/idea-edit-form";
import { fetchIdeaDetail } from "@/lib/queries/idea-detail";

export default async function IdeaEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const idea = await fetchIdeaDetail(id);
  if (!idea.isAuthor) notFound();

  return (
    <IdeaEditForm
      ideaId={idea.id}
      initialTitle={idea.title}
      initialContent={idea.body}
    />
  );
}
