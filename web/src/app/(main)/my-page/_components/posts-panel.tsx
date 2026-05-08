import { PostCard, type Post } from "@/components/post/post-card";

export function PostsPanel({ posts }: { posts: Post[] }) {
  if (posts.length === 0) {
    return (
      <div className="flex h-32 items-center justify-center rounded-md border border-border text-sm text-muted-foreground">
        아직 작성한 게시물이 없습니다.
      </div>
    );
  }
  return (
    <section className="flex flex-col gap-3">
      {posts.map((p) => (
        <PostCard key={p.id} post={p} />
      ))}
    </section>
  );
}
