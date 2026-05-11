import { PostApplyCta } from "@/components/post/post-apply-cta";
import {
  PostComments,
  type PostComment,
} from "@/components/post/post-comments";
import { POST_TYPES, type PostType } from "@/components/post/post-card";

// TODO: Supabase에서 post 조회로 교체. 지금은 디자인 검증용 더미.
// 짝수 id는 BETA_RECRUIT, 홀수는 FREE로 demo (지원 CTA 표시 확인용).
const DUMMY_POST = {
  title: "병원 데이터랑 식단 기록 연결하는 서비스 만들어보는 중입니다",
  authorName: "박소은",
  publishedAt: "2026.05.06 게시",
  body: `고지혈증 관리할 때 병원 데이터랑 일상 식단이 따로 노는 게 아쉬워서
둘을 연결해보는 서비스 아이디어 구상 중이에요

식단 기록 → 수치 변화까지 이어서 보여주면 의미 있을 것 같은데
이게 실제로 유의미한 데이터가 나올지 고민이네요
비슷한 거 만들어보신 분 있을까요?`,
};

const DUMMY_COMMENTS: PostComment[] = [
  {
    id: "c1",
    authorName: "김태훈",
    body: "비슷한 거 PoC 만들어본 적 있어요. 데이터 정제가 제일 어려웠습니다.",
    createdAt: "2시간 전",
  },
  {
    id: "c2",
    authorName: "이서연",
    body: "환자 동의 받는 과정만 잘 풀면 임상적으로 의미 있을 것 같아요.",
    createdAt: "30분 전",
  },
];

function postTypeFor(id: string): PostType {
  const n = Number(id);
  if (!Number.isFinite(n)) return "FREE";
  return n % 2 === 0 ? "BETA_RECRUIT" : "FREE";
}

export default async function BoardDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  // TODO: Supabase에서 id로 post 조회. 지금은 mock + id 기반 postType 분기.
  const postType = postTypeFor(id);
  const post = { ...DUMMY_POST, id, postType };
  const typeLabel =
    POST_TYPES.find((t) => t.value === postType)?.label ?? postType;
  const showApply = postType === "BETA_RECRUIT" || postType === "DEV_RECRUIT";

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-6">
        <header className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-4">
            <p className="text-xs leading-[1.5] tracking-[-0.3px] text-muted-foreground">
              {typeLabel}
            </p>
            {showApply ? (
              <PostApplyCta postType={postType} postId={post.id} />
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

        <PostComments initial={DUMMY_COMMENTS} />
      </div>
    </main>
  );
}
