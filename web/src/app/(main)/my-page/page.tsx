import { redirect } from "next/navigation";

import { MyPageTabs } from "./_components/my-page-tabs";
import { fetchMyPageData } from "@/lib/queries/my-page";

export default async function MyPage() {
  const data = await fetchMyPageData();
  if (!data) redirect("/login");
  // posts 테이블은 아직 미구현 — 빈 배열로 두고 EmptyState 가 노출
  return <MyPageTabs data={{ ...data, posts: [] }} />;
}
