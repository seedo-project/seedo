import { IdeaFeed } from "@/components/idea/idea-feed";
import type { Idea } from "@/components/idea/idea-card";

// TODO: Supabase에서 아이디어 피드 + 본인 구매 내역 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_IDEAS: Idea[] = [
  {
    id: "1",
    variant: "default",
    tags: ["음악🎵", "SNS", "친구초대", "2030", "트렌드"],
    postedAt: "10분 전",
    priceCredits: 100,
  },
  {
    id: "2",
    variant: "purchased",
    title: "고지혈증 환자를 위한 병원 연동 식단 관리 서비스",
    description:
      "병원 데이터와 일상 식단을 연결해 만성질환 관리를 자동화. #건강 #의료 #식단",
    purchasedByMe: true,
  },
  {
    id: "3",
    variant: "default",
    tags: ["취미", "공방", "오프라인모임", "도시생활"],
    postedAt: "27분 전",
    priceCredits: 80,
  },
  {
    id: "4",
    variant: "default",
    tags: ["반려동물", "산책", "GPS", "위치공유", "동네"],
    postedAt: "1시간 전",
    priceCredits: 120,
  },
  {
    id: "5",
    variant: "purchased",
    title: "프리랜서 정산·세금 자동 계산 SaaS",
    description:
      "카드사 API 연동으로 입금 분류 + 분기별 세금 예상치 미리 계산. #프리랜서 #세금 #자동화",
    purchasedByMe: false,
  },
  {
    id: "6",
    variant: "default",
    tags: ["1인가구", "식단", "큐레이션", "마트", "장보기"],
    postedAt: "2시간 전",
    priceCredits: 100,
  },
  {
    id: "7",
    variant: "default",
    tags: ["독서모임", "페이스메이커", "생산성"],
    postedAt: "어제",
    priceCredits: 90,
  },
  {
    id: "8",
    variant: "default",
    tags: ["헬스장", "PT", "기록", "트레이너", "공유"],
    postedAt: "어제",
    priceCredits: 110,
  },
  {
    id: "9",
    variant: "default",
    tags: ["공연", "티켓", "양도", "마켓플레이스"],
    postedAt: "2일 전",
    priceCredits: 130,
  },
];

export default function IdeaPage() {
  return <IdeaFeed ideas={DUMMY_IDEAS} />;
}
