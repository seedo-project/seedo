"use client";

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

import { IdeasPanel } from "./ideas-panel";
import { PostsPanel } from "./posts-panel";
import { ProfilePanel, type ProfileMock } from "./profile-panel";
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

export function MyPageTabs({ data }: { data: MyPageData }) {
  const [active, setActive] = useState<TabValue>("profile");
  const headerLabel =
    TABS.find((t) => t.value === active)?.header ?? "마이페이지";

  return (
    <main className="pt-10 pb-16 pl-[100px]">
      <Tabs
        value={active}
        onValueChange={(v) => setActive(v as TabValue)}
        orientation="vertical"
        className="!gap-5"
      >
        <TabsList
          variant="line"
          aria-label="마이페이지 탭"
          className="!h-fit w-[190px] shrink-0 !gap-0 !bg-transparent !p-0 self-start mt-[68px]"
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

        <div className="flex w-[820px] flex-col">
          <header className="flex h-9 w-full items-center justify-between">
            <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
              {headerLabel}
            </h1>
            {active === "profile" && (
              <button
                type="button"
                className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
              >
                변경사항 저장
              </button>
            )}
          </header>

          <div className="mt-8">
            <TabsContent value="profile" className="flex justify-center">
              <div className="w-[400px]">
                <ProfilePanel profile={data.profile} />
              </div>
            </TabsContent>
            <TabsContent value="ideas">
              <IdeasPanel ideas={data.ideas} />
            </TabsContent>
            <TabsContent value="projects">
              <div className="w-[612px]">
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
