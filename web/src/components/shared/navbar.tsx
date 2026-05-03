import Image from "next/image";
import Link from "next/link";

type NavKey = "idea" | "feed" | "board" | "my-page";

const NAV_ITEMS: { key: NavKey; label: string; href: string }[] = [
  { key: "idea", label: "아이디어", href: "/idea" },
  { key: "feed", label: "피드", href: "/feed" },
  { key: "board", label: "보드", href: "/board" },
  { key: "my-page", label: "마이페이지", href: "/my-page" },
];

export function Navbar({ current }: { current: NavKey }) {
  return (
    <header className="h-[108px] w-full border-b border-[#d4d4d8] px-[100px] py-[25px]">
      <div className="flex w-full items-start justify-between pt-5">
        <Link href="/idea" aria-label="Seedo 홈" className="block">
          <Image
            src="/seedo/logo-nav.svg"
            alt="Seedo"
            width={118}
            height={30}
            priority
            className="h-[29.591px] w-[118px]"
          />
        </Link>
        <nav className="flex items-center gap-10">
          {NAV_ITEMS.map(({ key, label, href }) => {
            const active = key === current;
            return (
              <Link
                key={key}
                href={href}
                className="relative flex items-center justify-center rounded-md px-3 py-2"
              >
                <span
                  className={`text-[15px] leading-[1.5] font-bold tracking-[-0.375px] ${
                    active ? "text-[#27272a]" : "text-[#71717a]"
                  }`}
                >
                  {label}
                </span>
                {active && (
                  <span
                    aria-hidden
                    className="absolute -bottom-0.5 size-1 rounded-full bg-primary"
                  />
                )}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
