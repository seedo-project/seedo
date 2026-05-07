"use client";

import { ChevronDown, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";

const POST_TYPES = [
  { value: "FREE", label: "자유 게시판" },
  { value: "PROMO", label: "홍보 게시판" },
  { value: "BETA_RECRUIT", label: "베타 테스터 모집" },
  { value: "DEV_RECRUIT", label: "개발자 모집" },
] as const;

type PostType = (typeof POST_TYPES)[number]["value"];

type Post = {
  id: string;
  postType: PostType;
  title: string;
  preview: string;
  timestamp: string;
};

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
  const [selectedType, setSelectedType] = useState<PostType>("FREE");
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 외부 클릭 시 dropdown 닫기
  useEffect(() => {
    if (!dropdownOpen) return;
    const handler = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setDropdownOpen(false);
      }
    };
    window.addEventListener("mousedown", handler);
    return () => window.removeEventListener("mousedown", handler);
  }, [dropdownOpen]);

  const selectedLabel = POST_TYPES.find((t) => t.value === selectedType)?.label;

  const visiblePosts = DUMMY_POSTS.filter((p) => p.postType === selectedType)
    .filter((p) => {
      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase();
      return (
        p.title.toLowerCase().includes(q) ||
        p.preview.toLowerCase().includes(q)
      );
    });

  return (
    <main className="mx-auto w-[820px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between">
        <h1 className="text-2xl leading-[1.5] font-bold tracking-[-0.6px] text-foreground">
          게시판
        </h1>
        <button
          type="button"
          className="flex h-9 items-center justify-center rounded-md bg-primary px-4 py-2 text-sm leading-[1.3] font-semibold tracking-[-0.35px] text-primary-foreground hover:bg-primary/90"
        >
          글 작성하기?
        </button>
      </div>

      <div className="mt-12 flex w-full items-center justify-between">
        <div ref={dropdownRef} className="relative w-[190px]">
          <button
            type="button"
            onClick={() => setDropdownOpen((v) => !v)}
            aria-expanded={dropdownOpen}
            aria-haspopup="listbox"
            className="flex h-10 w-full items-center justify-between rounded-md border border-border bg-card px-3 py-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground"
          >
            <span>{selectedLabel}</span>
            <ChevronDown className="size-3 text-muted-foreground" aria-hidden />
          </button>
          {dropdownOpen && (
            <ul
              role="listbox"
              className="absolute top-full left-0 z-10 mt-1 w-full overflow-hidden rounded-md border border-input bg-card py-1 shadow-lg"
            >
              {POST_TYPES.map((t) => {
                const active = t.value === selectedType;
                return (
                  <li key={t.value}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={active}
                      onClick={() => {
                        setSelectedType(t.value);
                        setDropdownOpen(false);
                      }}
                      className={`flex w-full items-center px-3 py-2 text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground hover:bg-muted ${
                        active ? "bg-muted" : ""
                      }`}
                    >
                      {t.label}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex h-10 w-[295px] items-center rounded-md border border-border bg-card pr-2 pl-3">
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="게시판 글을 검색하세요.."
            className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          <Search className="size-6 text-muted-foreground" aria-hidden />
        </div>
      </div>

      <section className="mt-12 flex flex-col gap-3">
        {visiblePosts.length === 0 ? (
          <div className="flex h-32 items-center justify-center rounded-md border border-border text-sm text-muted-foreground">
            해당하는 게시글이 없습니다.
          </div>
        ) : (
          visiblePosts.map((p) => <PostCard key={p.id} post={p} />)
        )}
      </section>
    </main>
  );
}

function PostCard({ post }: { post: Post }) {
  const typeLabel =
    POST_TYPES.find((t) => t.value === post.postType)?.label ?? post.postType;
  return (
    <article className="flex h-[132px] flex-col items-start rounded-md border border-border px-5 py-4">
      <div className="flex h-[101px] w-full flex-col gap-0.5">
        <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
          {typeLabel}
        </p>
        <h3 className="line-clamp-1 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
          {post.title}
        </h3>
        <p className="line-clamp-2 h-9 text-xs leading-[1.5] font-medium tracking-[-0.3px] whitespace-pre-line text-muted-foreground">
          {post.preview}
        </p>
        <p className="text-[11px] leading-[1.5] tracking-[-0.275px] text-muted-foreground">
          {post.timestamp}
        </p>
      </div>
    </article>
  );
}
