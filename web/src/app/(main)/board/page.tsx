import { BoardView, type Post } from "@/components/post/board-view";

// TODO: Supabase에서 게시물 조회로 교체. 지금은 디자인 검증용 더미.
const DUMMY_POSTS: Post[] = [
  {
    id: "1",
    postType: "FREE",
    title: "병원 데이터랑 식단 기록 연결하는 서비스 만들어보는 중입니다",
    preview:
      "고지혈증 관리할 때 병원 데이터랑 일상 식단이 따로 노는 게 아쉬워서\n둘을 연결해보는 서비스 아이디어 구상 중이에요",
    timestamp: "29분 전",
  },
  {
    id: "2",
    postType: "PROMO",
    title: "새로운 가계부 SaaS 베타 오픈했어요",
    preview:
      "영수증 OCR + 카드사 연동 + 카테고리 자동 분류까지\n초대 코드 SEEDO 입력하면 3개월 무료입니다",
    timestamp: "1시간 전",
  },
  {
    id: "3",
    postType: "BETA_RECRUIT",
    title: "반려동물 산책 동선 공유 앱 베타테스터 30명 모집",
    preview:
      "iOS/Android 모두 가능. 일주일에 한 번 짧은 설문만 응답해 주시면 되고\n참여자 전원에게 기프티콘 드립니다",
    timestamp: "3시간 전",
  },
  {
    id: "4",
    postType: "DEV_RECRUIT",
    title: "사이드 프로젝트 같이 하실 백엔드 개발자 1분",
    preview:
      "Spring Boot + Supabase 스택. 주 5시간 정도, 6주 진행 예정\nMVP 채택되면 운영 비용 분배합니다",
    timestamp: "어제",
  },
  {
    id: "5",
    postType: "FREE",
    title: "다들 처음 만든 앱 출시까지 얼마나 걸리셨어요?",
    preview:
      "혼자 사이드로 만들고 있는데 자꾸 일정이 늘어져서\n다른 분들 사례가 궁금합니다",
    timestamp: "어제",
  },
  {
    id: "6",
    postType: "PROMO",
    title: "Seedo 사용자 분들께 드리는 얼리액세스 안내",
    preview:
      "이번 달 안에 가입한 분들 한정으로 프리미엄 기능을 한 달 무료로 열어드립니다.\n자세한 내용은 댓글 참고해 주세요",
    timestamp: "2일 전",
  },
];

export default function BoardPage() {
  return <BoardView posts={DUMMY_POSTS} />;
}
