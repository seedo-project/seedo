"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import type { Idea } from "@/components/idea/idea-card";
import type { Post } from "@/components/post/post-card";
import type { Project } from "@/components/project/project-card";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

import { IdeasPanel } from "./ideas-panel";
import { PostsPanel } from "./posts-panel";
import {
  ProfilePanel,
  type ProfileEditableFields,
  type ProfileMock,
} from "./profile-panel";
import { ProjectsPanel } from "./projects-panel";

const TABS = [
  { value: "profile", label: "내 정보", header: "내 정보" },
  { value: "ideas", label: "내 아이디어", header: "내 아이디어" },
  { value: "projects", label: "내 프로젝트", header: "내 프로젝트" },
  { value: "posts", label: "내 게시물", header: "내 게시물" },
] as const;

type TabValue = (typeof TABS)[number]["value"];

export type MyPageData = {
  profile: ProfileMock;
  ideas: Idea[];
  projects: Project[];
  posts: Post[];
};

function pad2(s: string) {
  return s.length === 1 ? `0${s}` : s;
}

function buildBirthDate(y: string, m: string, d: string): string | null {
  if (!y && !m && !d) return null;
  if (!y || !m || !d) return null;
  const year = Number(y);
  const month = Number(m);
  const day = Number(d);
  if (
    !Number.isInteger(year) ||
    !Number.isInteger(month) ||
    !Number.isInteger(day)
  )
    return null;
  if (year < 1900 || year > 2100) return null;
  if (month < 1 || month > 12) return null;
  if (day < 1 || day > 31) return null;
  const normalized = `${year}-${pad2(String(month))}-${pad2(String(day))}`;
  const date = new Date(`${normalized}T00:00:00Z`);
  if (
    Number.isNaN(date.getTime()) ||
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() + 1 !== month ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return normalized;
}

function genderToMeta(g: ProfileMock["gender"]): string | null {
  if (g === "MALE") return "male";
  if (g === "FEMALE") return "female";
  return null;
}

export function MyPageTabs({ data }: { data: MyPageData }) {
  const router = useRouter();
  const [active, setActive] = useState<TabValue>("profile");
  const [profile, setProfile] = useState<ProfileEditableFields>({
    name: data.profile.name,
    birthYear: data.profile.birthYear,
    birthMonth: data.profile.birthMonth,
    birthDay: data.profile.birthDay,
    gender: data.profile.gender,
  });
  const [saving, setSaving] = useState(false);

  const headerLabel =
    TABS.find((t) => t.value === active)?.header ?? "마이페이지";

  const handleSave = async () => {
    if (saving) return;
    if (!profile.name.trim()) {
      toast.error("이름을 입력하세요");
      return;
    }
    const birthDate = buildBirthDate(
      profile.birthYear,
      profile.birthMonth,
      profile.birthDay,
    );
    if (
      (profile.birthYear || profile.birthMonth || profile.birthDay) &&
      !birthDate
    ) {
      toast.error("생년월일을 올바르게 입력하세요");
      return;
    }

    setSaving(true);
    try {
      const supabase = createClient();
      const { error } = await supabase.auth.updateUser({
        data: {
          name: profile.name.trim(),
          birth_date: birthDate,
          gender: genderToMeta(profile.gender),
        },
      });
      if (error) throw error;
      toast.success("변경사항을 저장했습니다");
      router.refresh();
    } catch (e) {
      const msg = e instanceof Error ? e.message : "저장에 실패했습니다";
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="px-4 pt-10 pb-16 md:pl-[100px] md:pr-0">
      <Tabs
        value={active}
        onValueChange={(v) => setActive(v as TabValue)}
        orientation="vertical"
        className="!flex-col !gap-5 md:!flex-row"
      >
        <TabsList
          variant="line"
          aria-label="마이페이지 탭"
          className="no-scrollbar !h-fit w-full shrink-0 !flex-row overflow-x-auto !gap-0 !bg-transparent !p-0 md:w-[190px] md:!flex-col md:self-start md:mt-[68px]"
        >
          {TABS.map((tab) => (
            <TabsTrigger
              key={tab.value}
              value={tab.value}
              className="!h-auto w-full justify-start rounded-md px-3 py-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground !shadow-none hover:text-foreground data-active:!bg-zinc-100 data-active:text-zinc-600"
            >
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <div className="flex w-full max-w-[820px] flex-col">
          <header className="flex h-9 w-full items-center justify-between">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              {headerLabel}
            </h1>
            {active === "profile" && (
              <button
                type="button"
                onClick={handleSave}
                disabled={saving}
                className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90 disabled:opacity-60"
              >
                {saving ? "저장 중..." : "변경사항 저장"}
              </button>
            )}
          </header>

          <div className="mt-8">
            <TabsContent value="profile" className="flex justify-center">
              <div className="w-full max-w-[400px]">
                <ProfilePanel
                  values={profile}
                  onChange={setProfile}
                  email={data.profile.email}
                />
              </div>
            </TabsContent>
            <TabsContent value="ideas">
              <IdeasPanel ideas={data.ideas} />
            </TabsContent>
            <TabsContent value="projects">
              <div className="w-full max-w-[612px]">
                <ProjectsPanel projects={data.projects} />
              </div>
            </TabsContent>
            <TabsContent value="posts">
              <PostsPanel posts={data.posts} />
            </TabsContent>
          </div>
        </div>
      </Tabs>
    </main>
  );
}
