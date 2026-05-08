import Link from "next/link";

export const POST_TYPES = [
  { value: "FREE", label: "자유 게시판" },
  { value: "PROMO", label: "홍보 게시판" },
  { value: "BETA_RECRUIT", label: "베타 테스터 모집" },
  { value: "DEV_RECRUIT", label: "개발자 모집" },
] as const;

export type PostType = (typeof POST_TYPES)[number]["value"];

export type Post = {
  id: string;
  postType: PostType;
  title: string;
  preview: string;
  timestamp: string;
  createdAt: string;
};

export function PostCard({ post }: { post: Post }) {
  const typeLabel =
    POST_TYPES.find((t) => t.value === post.postType)?.label ?? post.postType;
  return (
    <Link
      href={`/board/${post.id}`}
      className="rounded-md focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:outline-none"
    >
      <article className="flex h-[132px] flex-col items-start rounded-md border border-border px-5 py-4 transition-colors hover:bg-muted/50">
        <div className="flex h-[101px] w-full flex-col gap-0.5">
          <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
            {typeLabel}
          </p>
          <h3 className="line-clamp-1 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
            {post.title}
          </h3>
          <p className="line-clamp-2 h-9 text-xs leading-[1.5] font-medium tracking-[-0.3px] whitespace-pre-line text-muted-foreground">
            {post.preview}
          </p>
          <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
            {post.timestamp}
          </p>
        </div>
      </article>
    </Link>
  );
}
