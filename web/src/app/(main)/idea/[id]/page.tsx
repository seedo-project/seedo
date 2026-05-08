// TODO: Supabase에서 idea 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_IDEA = {
  id: "1",
  title: "고지혈증 환자를 위한 병원 연동 식단 관리 서비스",
  authorName: "박소은",
  publishedAt: "2026.05.06 게시",
  body: `# Problem
고지혈증 환자는 지속적인 식단 관리가 필수적이지만,
현실적으로는 병원 진료 이후 일상에서의 관리가 제대로 이루어지지 않는다.

- 병원에서는 검사 결과와 약 처방 위주의 관리가 이루어짐
- 환자는 일상 식단이 자신의 건강 상태에 어떤 영향을 주는지 인지하기 어려움
- 식단 기록 앱은 많지만, 질환 특화 피드백이 부족함
- 의료진과 환자 간의 지속적인 연결이 단절됨

# Market
환자는 꾸준히 증가하고 있으며, 만성질환 관리 시장 또한 빠르게 성장하고 있다.

- 국내 성인 3명 중 1명 이상이 이상지질혈증 위험군
- 헬스케어 앱 시장은 성장 중이지만, 질환 특화 서비스는 제한적
- 병원은 사후 관리 서비스 확장에 대한 니즈가 있음

# Target User
- 30~50대 직장인
- 건강검진에서 고지혈증 진단을 받은 초기 환자
- 식단 관리를 해야 하지만 지속하지 못하는 사용자

# Insight
"환자는 '무엇을 먹지 말아야 하는지'는 알지만,
'지금 먹는 것이 내 몸에 어떤 영향을 주는지'는 모른다."

→ 정보는 있지만, 맥락화된 피드백이 부족함

# Solution
병원과 연결된 고지혈증 특화 식단 관리 서비스

- 사용자의 식단을 기록하면, 고지혈증 기준으로 자동 분석
- 병원 데이터(혈액 검사 결과 등)와 연동하여 개인 맞춤 피드백 제공
- 의료진이 환자의 식단 데이터를 확인 가능`,
};

export default async function IdeaDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  // TODO: Supabase에서 id로 조회 + 구매 권한 검증
  const idea = { ...DUMMY_IDEA, id };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-4">
        <header className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-4">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              {idea.title}
            </h1>
            <button
              type="button"
              disabled
              aria-disabled="true"
              title="프로젝트 시작 — 채택 트랜잭션 별도 작업"
              className="flex h-9 cursor-not-allowed items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] whitespace-nowrap text-primary-foreground opacity-50"
            >
              프로젝트 시작하기
            </button>
          </div>
          <div className="flex items-center gap-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            <span>{idea.authorName}</span>
            <span aria-hidden className="h-2 w-px bg-border" />
            <span>{idea.publishedAt}</span>
          </div>
        </header>

        <article className="h-[776px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {idea.body}
        </article>
      </div>
    </main>
  );
}
