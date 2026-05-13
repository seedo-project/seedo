import { redirect } from "next/navigation";

import { MyPageTabs } from "./_components/my-page-tabs";
import { fetchMyPageData } from "@/lib/queries/my-page";

export default async function MyPage() {
  const data = await fetchMyPageData();
  if (!data) redirect("/login");
  return <MyPageTabs data={data} />;
}
