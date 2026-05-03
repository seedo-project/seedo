import { Navbar } from "@/components/shared/navbar";
import { ProjectCard, type Project } from "@/components/project/project-card";

// TODO: Supabase에서 프로젝트 피드 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_PROJECTS: Project[] = Array.from({ length: 10 }, (_, i) => ({
  id: String(i + 1),
  title: "서울 3대 밀크티 맛집을, 집에서 그대로 [원산지:선물 상세 정보 참고]",
  subtitle: "프뤼떼마지 FRUITE' MAGIE'",
  description:
    "안녕하세요 프뤼떼마지 입니다. '프뤼떼마지'는 19세기 영국왕실의 티를 현대적으로 재해석한 프리미엄 과일밀크티 전문 브랜드 입니다. '누구나 즐길 수 있는 밀크티' 를 만들고 싶은 마음으로 시작 했습니다. ( 홍차를 연구하고, 티를 블랜딩하여, 직접 제조 합니다 ) 단순히 재료를 섞는 것이 아닌 '좋...",
  thumbnailUrl: null,
  statuses: ["in-progress", "hype"],
}));

export default function FeedPage() {
  return (
    <>
      <Navbar current="feed" />
      <main className="px-[100px] pt-8 pb-16">
        <div className="flex w-full items-center justify-between pr-3">
          <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-[#27272a]">
            지금 뜨는 프로젝트? 뭐 이런..
          </h1>
          <button
            type="button"
            className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-[#fafafa] hover:bg-primary/90"
          >
            프로젝트 게시
          </button>
        </div>

        <section className="mt-8 grid grid-cols-2 gap-4">
          {DUMMY_PROJECTS.map((p) => (
            <ProjectCard key={p.id} project={p} />
          ))}
        </section>
      </main>
    </>
  );
}
