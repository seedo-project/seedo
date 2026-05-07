"use client";

import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";

const TABS = [
  { value: "profile", label: "내 정보" },
  { value: "ideas", label: "내 아이디어" },
  { value: "projects", label: "내 프로젝트" },
  { value: "posts", label: "내 게시물" },
] as const;

export type MyPageUser = {
  nickname: string;
  email: string;
  creditBalance: number;
};

export function MyPageTabs({ user }: { user: MyPageUser }) {
  return (
    <main className="px-[100px] pt-10 pb-16">
      <Tabs
        defaultValue="profile"
        orientation="vertical"
        className="gap-10"
      >
        <TabsList
          variant="line"
          aria-label="마이페이지 탭"
          className="!h-fit w-[190px] shrink-0 !gap-0 !bg-transparent !p-0"
        >
          {TABS.map((tab) => (
            <TabsTrigger
              key={tab.value}
              value={tab.value}
              className="!h-auto w-full justify-start px-3 py-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground !shadow-none hover:text-foreground data-active:!bg-transparent data-active:text-foreground"
            >
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="profile" className="flex-1">
          <ProfilePanel user={user} />
        </TabsContent>
        <TabsContent value="ideas" className="flex-1">
          <EmptyPanel label="아이디어" />
        </TabsContent>
        <TabsContent value="projects" className="flex-1">
          <EmptyPanel label="프로젝트" />
        </TabsContent>
        <TabsContent value="posts" className="flex-1">
          <EmptyPanel label="게시물" />
        </TabsContent>
      </Tabs>
    </main>
  );
}

function ProfilePanel({ user }: { user: MyPageUser }) {
  return (
    <div className="flex max-w-[640px] flex-col gap-10">
      <Section title="프로필">
        <Row label="닉네임" value={user.nickname} />
        <Row label="이메일" value={user.email} />
      </Section>
      <Section title="크레딧">
        <Row
          label="잔액"
          value={`${user.creditBalance.toLocaleString("ko-KR")} 크레딧`}
        />
      </Section>
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg leading-[1.5] font-semibold tracking-[-0.45px] text-foreground">
        {title}
      </h2>
      <div className="flex flex-col">{children}</div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex w-full items-center justify-between border-b border-border py-3">
      <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
        {label}
      </span>
      <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-foreground">
        {value}
      </span>
    </div>
  );
}

function EmptyPanel({ label }: { label: string }) {
  const particle = subjectParticle(label);
  return (
    <div className="flex h-64 flex-col items-center justify-center gap-2 rounded-md border border-border">
      <p className="text-sm font-medium tracking-[-0.35px] text-muted-foreground">
        아직 {label}
        {particle} 없습니다.
      </p>
    </div>
  );
}

// 받침에 따라 주격조사를 결정한다 (한글 마지막 음절의 jongseong 사용; 비한글은 기본값 "가").
function subjectParticle(word: string): "이" | "가" {
  if (!word.length) return "가";
  const lastChar = word.charCodeAt(word.length - 1);
  if (lastChar < 0xac00 || lastChar > 0xd7a3) return "가";
  const jongseong = (lastChar - 0xac00) % 28;
  return jongseong !== 0 ? "이" : "가";
}
