import { BoardView } from "@/components/post/board-view";
import { fetchPosts } from "@/lib/queries/posts";

export default async function BoardPage() {
  const posts = await fetchPosts();
  return <BoardView posts={posts} />;
}
