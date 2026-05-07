import { Bookmark, Star } from "lucide-react";

import { ChipStatus, type ChipVariant } from "@/components/project/chip-status";

// TODO: Supabase에서 project 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_PROJECT = {
  id: "1",
  status: "verifying" as ChipVariant,
  title: "고지혈증 환자를 위한 병원 마이 데이터 연동 식단 관리 서비스",
  authorName: "박소은",
  description:
    "고지혈증 관리를 위해 식단과 의료 데이터를 연결하는 아이디어. 식단 관리 서비스 제안 및 병원 데이터와 연결된 일상 속 건강 관리 가능성을 함께 살펴봅니다. 환자가 스스로 식단을 기록하고 의료진 피드백을 받을 수 있는 통합 건강 관리 플랫폼 컨셉.",
  registeredAt: "2026.05.06 등록",
  bookmarkCount: 2899,
  hypeCount: 2899,
  body: `# Problem
고지혈증 환자는 지속적인 식단 관리가 필수적이지만,
현실적으로는 병원 진료 이후 일상에서의 관리가 제대로 이루어지지 않는다.

- 병원에서는 검사 결과와 약 처방 위주의 관리가 이루어짐
- 환자는 일상 식단이 자신의 건강 상태에 어떤 영향을 주는지 인지하기 어려움
- 식단 기록 앱은 많지만, 질환 특화 피드백이 부족함

# Market
환자는 꾸준히 증가하고 있으며, 만성질환 관리 시장 또한 빠르게 성장하고 있다.

- 국내 성인 3명 중 1명 이상이 이상지질혈증 위험군
- 헬스케어 앱 시장은 성장 중이지만, 질환 특화 서비스는 제한적

# Solution
병원과 연결된 고지혈증 특화 식단 관리 서비스
- 사용자의 식단을 기록하면, 고지혈증 기준으로 자동 분석
- 병원 데이터(혈액 검사 결과 등)와 연동하여 개인 맞춤 피드백 제공`,
};

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  // TODO: Supabase에서 id로 project 조회
  const project = { ...DUMMY_PROJECT, id };

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex flex-col gap-8">
        <div className="flex w-full gap-10">
          <div
            className="size-[295px] shrink-0 rounded-lg bg-muted"
            aria-hidden
          />
          <div className="flex flex-1 flex-col items-end justify-between">
            <div className="flex w-full flex-col gap-1.5">
              <div className="flex items-start gap-1.5">
                <ChipStatus variant={project.status} />
              </div>
              <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
                {project.title}
              </h1>
              <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
                {project.authorName}
              </p>
              <p className="line-clamp-3 text-base leading-[1.5] font-medium tracking-[-0.4px] text-muted-foreground">
                {project.description}
              </p>
              <p className="text-xs leading-[1.5] font-medium tracking-[-0.3px] text-muted-foreground/70">
                {project.registeredAt}
              </p>
            </div>
            <div className="flex items-center">
              <button
                type="button"
                aria-label={`북마크 ${project.bookmarkCount}회`}
                className="flex size-[60px] flex-col items-center justify-center text-muted-foreground hover:text-foreground"
              >
                <Bookmark className="size-6" aria-hidden />
                <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
                  {project.bookmarkCount.toLocaleString("ko-KR")}
                </span>
              </button>
              <button
                type="button"
                aria-label={`Hype ${project.hypeCount}회`}
                className="flex size-[60px] flex-col items-center justify-center text-muted-foreground hover:text-foreground"
              >
                <Star className="size-6" aria-hidden />
                <span className="text-xs leading-[1.3] font-bold tracking-[-0.3px]">
                  {project.hypeCount.toLocaleString("ko-KR")}
                </span>
              </button>
            </div>
          </div>
        </div>

        <article className="h-[520px] overflow-y-auto rounded-md border border-border p-4 text-base leading-[1.5] tracking-[-0.4px] whitespace-pre-line text-muted-foreground">
          {project.body}
        </article>
      </div>
    </main>
  );
}
