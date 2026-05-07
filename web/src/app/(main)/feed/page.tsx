import { ProjectCard, type Project } from "@/components/project/project-card";

// TODO: Supabase에서 프로젝트 피드 조회로 교체. 지금은 디자인 검증용 더미.
// statuses는 Figma 디자인의 4가지 chip 변형(in-progress / verifying / completed / hype)을
// 골고루 보여주도록 다양화.
const DUMMY_PROJECTS: Project[] = [
  {
    id: "1",
    title: "서울 3대 밀크티 맛집을, 집에서 그대로 [원산지:선물 상세 정보 참고]",
    subtitle: "프뤼떼마지 FRUITE' MAGIE'",
    description:
      "안녕하세요 프뤼떼마지 입니다. '프뤼떼마지'는 19세기 영국왕실의 티를 현대적으로 재해석한 프리미엄 과일밀크티 전문 브랜드 입니다. '누구나 즐길 수 있는 밀크티' 를 만들고 싶은 마음으로 시작 했습니다. ( 홍차를 연구하고, 티를 블랜딩하여, 직접 제조 합니다 ) 단순히 재료를 섞는 것이 아닌 '좋...",
    thumbnailUrl: null,
    statuses: ["in-progress", "hype"],
  },
  {
    id: "2",
    title: "동네 빵집 도장깨기 — 매일 새 빵집 한 곳 추천",
    subtitle: "빵피라이드 BAEKERY RIDE",
    description:
      "퇴근길에 들를 수 있는 동네 빵집을 매일 한 곳씩 추천해주는 앱을 만들고 있습니다. 사용자 위치 기반 + 빵 종류 필터 + 영업시간까지 한 번에 확인할 수 있도록 정리합니다. 베이커리 사장님들과 직접 협업해서 정확한 정보를 제공하는 것이 목표...",
    thumbnailUrl: null,
    statuses: ["verifying"],
  },
  {
    id: "3",
    title: "혼자 사는 사람들을 위한 1인분 식단 큐레이션 서비스",
    subtitle: "한그릇 HANGREUT",
    description:
      "1인 가구가 늘어나면서 식사 준비가 점점 부담이 되는 시대입니다. 한그릇은 1인분 기준으로 정량화된 레시피와 마트 장보기 리스트를 제공하여, 음식물 쓰레기 없이 매일 다른 메뉴를 즐길 수 있도록 돕습니다. 베타 사용자 200명을 모집...",
    thumbnailUrl: null,
    statuses: ["completed"],
  },
  {
    id: "4",
    title: "반려동물 산책 동선을 GPS로 자동 기록하고 공유하는 앱",
    subtitle: "산책로그 SANCHAEK LOG",
    description:
      "반려견과의 산책 경로를 자동으로 기록하고, 같은 동네 견주들과 추천 산책로를 공유할 수 있는 서비스입니다. 시간대별 산책 빈도, 날씨 연동 알림, 친구 견주와의 동행 기능까지 포함하여 산책 경험을 풍부하게 만드는 것이 목표...",
    thumbnailUrl: null,
    statuses: ["in-progress"],
  },
  {
    id: "5",
    title: "프리랜서를 위한 정산·세금 자동 계산 SaaS",
    subtitle: "프리택스 FREETAX",
    description:
      "프리랜서 작업자의 입금 내역을 자동으로 분류하고, 분기별 부가세·종합소득세 예상치를 미리 알려주는 서비스입니다. 카드사·은행 API 연동으로 영수증 입력을 최소화하고, 세무사 매칭까지 한 번에 처리할 수 있습니다...",
    thumbnailUrl: null,
    statuses: ["hype"],
  },
  {
    id: "6",
    title: "오프라인 모임 참여자들끼리 사진을 자동 공유하는 갤러리",
    subtitle: "모임샷 MOIM SHOT",
    description:
      "행사·모임 참여자가 같은 장소·시간대에 찍은 사진을 자동으로 모아주는 공유 갤러리입니다. QR 한 번 스캔으로 그룹에 참여하고, 행사 종료 후 모든 참여자의 사진을 한 곳에서 다운로드할 수 있게 합니다. 사진 정리에 들이는 시간을 0으로...",
    thumbnailUrl: null,
    statuses: ["in-progress", "hype"],
  },
  {
    id: "7",
    title: "독서 모임 참여자들의 읽기 속도를 맞춰주는 페이스 메이커",
    subtitle: "리딩페이스 READING PACE",
    description:
      "온라인 독서 모임을 운영할 때 가장 어려운 점이 진도 맞추기인데요. 책의 페이지·챕터를 기준으로 일정을 자동 설계하고, 참여자별 진도율을 시각화해서 누가 늦고 누가 빠른지 한눈에 보여주는 도구입니다...",
    thumbnailUrl: null,
    statuses: ["verifying"],
  },
  {
    id: "8",
    title: "회사 점심 메뉴를 동료들과 투표로 결정하는 슬랙 봇",
    subtitle: "점심메이커 LUNCH MAKER",
    description:
      "매일 점심 메뉴 정하는 데 30분씩 쓰는 팀들을 위한 슬랙 봇입니다. 후보 메뉴를 등록하고 5분 안에 투표를 마치면 가까운 식당까지 자동 추천. 알레르기·식단 제약도 함께 고려하는 것이 차별점...",
    thumbnailUrl: null,
    statuses: ["completed"],
  },
  {
    id: "9",
    title: "헬스장 PT 일정·식단·운동 기록을 한 번에 관리하는 노트",
    subtitle: "PT노트 PT NOTE",
    description:
      "PT 받는 사람과 트레이너가 동시에 기록하고 공유할 수 있는 가벼운 운동 노트입니다. 기록은 트레이너가, 식단은 사용자가, 변화는 둘 다 함께 추적합니다. 캘린더·체중 그래프·메모 한 화면으로 통합...",
    thumbnailUrl: null,
    statuses: ["in-progress"],
  },
  {
    id: "10",
    title: "음악 공연 입장권을 친구에게 자유롭게 양도하는 마켓플레이스",
    subtitle: "티켓엔드 TICKET END",
    description:
      "공연 일정이 갑자기 안 맞을 때, 본인 명의 티켓을 안전하게 친구에게 양도할 수 있는 마켓플레이스입니다. 결제·환불·본인 인증을 한 번에 처리하고, 양도 이력을 공연 주최측과도 공유하여 부정 거래를 차단합니다...",
    thumbnailUrl: null,
    statuses: ["hype"],
  },
];

export default function FeedPage() {
  return (
    <main className="px-[100px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between pr-3">
        <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
          지금 뜨는 프로젝트? 뭐 이런..
        </h1>
        <button
          type="button"
          className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
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
  );
}
