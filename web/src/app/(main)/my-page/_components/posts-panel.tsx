import { FileText } from "lucide-react";

import { EmptyState } from "@/components/shared/empty-state";
import { PostCard, type Post } from "@/components/post/post-card";

export function PostsPanel({ posts }: { posts: Post[] }) {
  if (posts.length === 0) {
    return <EmptyState icon={FileText} title="아직 작성한 게시물이 없습니다" />;
  }
  return (
    <section className="flex flex-col gap-3">
      {posts.map((p) => (
        <PostCard key={p.id} post={p} />
      ))}
    </section>
  );
}
