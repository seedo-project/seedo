// TODO: Supabase에서 post 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_POST = {
  id: "1",
  title: "병원 데이터랑 식단 기록 연결하는 서비스 만들어보는 중입니다",
  authorName: "박소은",
  publishedAt: "2026.05.06 게시",
  body: `고지혈증 관리할 때 병원 데이터랑 일상 식단이 따로 노는 게 아쉬워서
둘을 연결해보는 서비스 아이디어 구상 중이에요

식단 기록 → 수치 변화까지 이어서 보여주면 의미 있을 것 같은데
이게 실제로 유의미한 데이터가 나올지 고민이네요
비슷한 거 만들어보신 분 있을까요?`,
};

export default async function BoardDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  // TODO: Supabase에서 id로 post 조회. 지금은 디자인 검증용 더미.
  const post = { ...DUMMY_POST, id };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-4">
        <header className="flex flex-col gap-1">
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
            {post.title}
          </h1>
          <div className="flex items-center gap-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            <span>{post.authorName}</span>
            <span aria-hidden className="h-2 w-px bg-border" />
            <span>{post.publishedAt}</span>
          </div>
        </header>

        <article className="h-[776px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {post.body}
        </article>
      </div>
    </main>
  );
}
