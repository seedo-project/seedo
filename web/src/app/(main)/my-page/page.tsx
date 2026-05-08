import type { Idea } from "@/components/idea/idea-card";
import type { Post } from "@/components/post/post-card";
import type { Project } from "@/components/project/project-card";

import { MyPageTabs, type MyPageData } from "./_components/my-page-tabs";

// TODO: Supabase auth.user() + 본인이 작성한 ideas/projects/posts 조회로 교체. 지금은 디자인 검증용 mock.

const MOCK_PROFILE: MyPageData["profile"] = {
  name: "박소은",
  birthYear: "2000",
  birthMonth: "11",
  birthDay: "12",
  gender: "FEMALE",
  email: "soeun_park@korea.ac.kr",
};

const MOCK_IDEAS: Idea[] = [
  {
    id: "i1",
    variant: "default",
    tags: ["음악🎵", "가사공유", "플랫폼", "SNS"],
    postedAt: "10분 전",
    priceCredits: 100,
  },
  {
    id: "i2",
    variant: "purchased",
    title: "작가들이 시나리오 협업하는 플랫폼",
    description:
      "작가 둘 이상이 한 시나리오를 동시 편집하고, 변경 이력과 코멘트를 남길 수 있는 협업 도구.",
    purchasedByMe: true,
  },
  {
    id: "i3",
    variant: "default",
    tags: ["여행", "동행", "매칭", "현지인", "체험"],
    postedAt: "1시간 전",
    priceCredits: 100,
  },
  {
    id: "i4",
    variant: "default",
    tags: ["헬스", "PT", "기록", "식단", "트래킹"],
    postedAt: "2시간 전",
    priceCredits: 100,
  },
  {
    id: "i5",
    variant: "default",
    tags: ["반려동물", "산책", "GPS", "커뮤니티", "동네"],
    postedAt: "3시간 전",
    priceCredits: 100,
  },
  {
    id: "i6",
    variant: "default",
    tags: ["프리랜서", "세금", "정산", "자동화", "SaaS"],
    postedAt: "5시간 전",
    priceCredits: 100,
  },
];

const MOCK_PROJECTS: Project[] = [
  {
    id: "p1",
    title: "서울 3대 밀크티 맛집을, 집에서 그대로 [원산지:선물 상세 정보 참고]",
    subtitle: "프뤼떼마지 FRUITE' MAGIE'",
    description:
      "안녕하세요 프뤼떼마지 입니다. '프뤼떼마지'는 19세기 영국왕실의 티를 현대적으로 재해석한 프리미엄 과일밀크티 전문 브랜드 입니다.",
    thumbnailUrl: null,
    statuses: ["completed"],
  },
  {
    id: "p2",
    title: "동네 빵집 도장깨기 — 매일 새 빵집 한 곳 추천",
    subtitle: "빵피라이드 BAEKERY RIDE",
    description:
      "퇴근길에 들를 수 있는 동네 빵집을 매일 한 곳씩 추천해주는 앱을 만들고 있습니다.",
    thumbnailUrl: null,
    statuses: ["verifying"],
  },
  {
    id: "p3",
    title: "혼자 사는 사람들을 위한 1인분 식단 큐레이션 서비스",
    subtitle: "한그릇 HANGREUT",
    description:
      "1인 가구가 늘어나면서 식사 준비가 점점 부담이 되는 시대. 1인분 기준으로 정량화된 레시피와 마트 장보기 리스트를 제공.",
    thumbnailUrl: null,
    statuses: ["in-progress", "hype"],
  },
];

const MOCK_POSTS: Post[] = [
  {
    id: "post-1",
    postType: "FREE",
    title: "병원 데이터랑 식단 기록 연결하는 서비스 만들어보는 중입니다",
    preview:
      "고지혈증 관리할 때 병원 데이터랑 일상 식단이 따로 노는 게 아쉬워서\n둘을 연결해보는 서비스 아이디어 구상 중이에요",
    timestamp: "29분 전",
    createdAt: "2026-05-08T11:30:00Z",
  },
  {
    id: "post-2",
    postType: "PROMO",
    title: "프뤼떼마지 베타 테스터 모집합니다",
    preview:
      "프리미엄 과일밀크티 베타 테스터 30명 모집 중입니다.\n무료 시음 + 피드백 작성 부탁드려요.",
    timestamp: "2시간 전",
    createdAt: "2026-05-08T10:00:00Z",
  },
  {
    id: "post-3",
    postType: "DEV_RECRUIT",
    title: "1인 가구 식단 서비스 백엔드 개발자 찾아요",
    preview:
      "Spring Boot + Postgres 경험 1년 이상.\n사이드프로젝트로 시작해서 잘 되면 본격 창업 방향 검토 중입니다.",
    timestamp: "5시간 전",
    createdAt: "2026-05-08T07:00:00Z",
  },
];

const MOCK_DATA: MyPageData = {
  profile: MOCK_PROFILE,
  ideas: MOCK_IDEAS,
  projects: MOCK_PROJECTS,
  posts: MOCK_POSTS,
};

export default function MyPage() {
  return <MyPageTabs data={MOCK_DATA} />;
}
