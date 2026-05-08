import { MyPageTabs, type MyPageUser } from "./_components/my-page-tabs";

// TODO: Supabase auth.user() + user_credits 조회로 교체. 지금은 디자인 검증용 mock.
const MOCK_USER: MyPageUser = {
  nickname: "씨도유저",
  email: "user@seedo.dev",
  creditBalance: 100,
};

export default function MyPage() {
  return <MyPageTabs user={MOCK_USER} />;
}
