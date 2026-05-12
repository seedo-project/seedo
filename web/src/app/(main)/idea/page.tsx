import { IdeaFeed } from "@/components/idea/idea-feed";
import { fetchIdeaFeed } from "@/lib/queries/ideas";

export default async function IdeaPage() {
  const ideas = await fetchIdeaFeed();
  return <IdeaFeed ideas={ideas} />;
}
