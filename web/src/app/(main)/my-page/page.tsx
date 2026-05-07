"use client";

import { useState } from "react";

const TABS = [
  { id: "profile", label: "내 정보" },
  { id: "ideas", label: "내 아이디어" },
  { id: "projects", label: "내 프로젝트" },
  { id: "posts", label: "내 게시물" },
] as const;

type TabId = (typeof TABS)[number]["id"];

// TODO: Supabase auth.user() + user_credits 조회로 교체. 지금은 디자인 검증용 mock.
const MOCK_USER = {
  nickname: "씨도유저",
  email: "user@seedo.dev",
  creditBalance: 100,
};

export default function MyPage() {
  const [activeTab, setActiveTab] = useState<TabId>("profile");

  return (
    <main className="px-[100px] pt-10 pb-16">
      <div className="flex gap-10">
        <nav className="flex w-[190px] shrink-0 flex-col items-start" aria-label="마이페이지 탭">
          {TABS.map((tab) => {
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                aria-current={active ? "page" : undefined}
                className={`flex w-full items-center px-3 py-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] transition-colors ${
                  active
                    ? "text-foreground"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </nav>

        <section className="flex-1">
          {activeTab === "profile" && <ProfilePanel />}
          {activeTab === "ideas" && <EmptyPanel label="아이디어" />}
          {activeTab === "projects" && <EmptyPanel label="프로젝트" />}
          {activeTab === "posts" && <EmptyPanel label="게시물" />}
        </section>
      </div>
    </main>
  );
}

function ProfilePanel() {
  return (
    <div className="flex max-w-[640px] flex-col gap-10">
      <Section title="프로필">
        <Row label="닉네임" value={MOCK_USER.nickname} />
        <Row label="이메일" value={MOCK_USER.email} />
      </Section>
      <Section title="크레딧">
        <Row
          label="잔액"
          value={`${MOCK_USER.creditBalance.toLocaleString()} 크레딧`}
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
  return (
    <div className="flex h-64 flex-col items-center justify-center gap-2 rounded-md border border-border">
      <p className="text-sm font-medium tracking-[-0.35px] text-muted-foreground">
        아직 {label}이 없습니다.
      </p>
    </div>
  );
}
