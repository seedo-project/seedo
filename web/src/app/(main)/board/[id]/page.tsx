import { CommentSection } from "@/components/shared/comment-section";
import { POST_TYPES } from "@/components/post/post-card";
import { PostApplicantsList } from "@/components/post/post-applicants-list";
import { PostApplyCta } from "@/components/post/post-apply-cta";
import { fetchComments } from "@/lib/queries/comments";
import { fetchPostApplicationState } from "@/lib/queries/post-application";
import { fetchPostApplicants } from "@/lib/queries/post-applicants";
import { fetchPostDetail } from "@/lib/queries/posts";

export default async function BoardDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const post = await fetchPostDetail(id);
  const postIdNum = Number(post.id);
  const isRecruit =
    post.postType === "BETA_RECRUIT" || post.postType === "DEV_RECRUIT";
  const showApply = isRecruit && !post.isAuthor;
  const showApplicants = isRecruit && post.isAuthor;
  const [comments, application, applicants] = await Promise.all([
    fetchComments("post", postIdNum),
    showApply
      ? fetchPostApplicationState(postIdNum)
      : Promise.resolve({ applied: false }),
    showApplicants ? fetchPostApplicants(postIdNum) : Promise.resolve([]),
  ]);

  const typeLabel =
    POST_TYPES.find((t) => t.value === post.postType)?.label ?? post.postType;

  return (
    <main className="mx-auto w-full max-w-[820px] px-4 pt-8 pb-16 md:px-0">
      <div className="flex flex-col gap-6">
        <header className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-4">
            <p className="text-xs leading-[1.5] tracking-[-0.3px] text-muted-foreground">
              {typeLabel}
            </p>
            {showApply ? (
              <PostApplyCta
                postType={post.postType}
                postId={post.id}
                initialApplied={application.applied}
              />
            ) : null}
          </div>
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
            {post.title}
          </h1>
          <div className="flex items-center gap-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            <span>{post.authorName}</span>
            <span aria-hidden className="h-2 w-px bg-border" />
            <span>{post.publishedAt}</span>
          </div>
        </header>

        <article className="overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {post.body}
        </article>

        {showApplicants ? (
          <PostApplicantsList applicants={applicants} />
        ) : null}

        <CommentSection
          target="post"
          targetId={postIdNum}
          initialComments={comments}
        />
      </div>
    </main>
  );
}
