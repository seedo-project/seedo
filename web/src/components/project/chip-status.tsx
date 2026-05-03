import Image from "next/image";

export type ChipVariant = "in-progress" | "verifying" | "completed" | "hype";

const VARIANTS: Record<
  ChipVariant,
  { label: string; bg: string; text: string; icon?: string }
> = {
  "in-progress": {
    label: "진행 중",
    bg: "bg-emerald-100",
    text: "text-emerald-500",
  },
  verifying: {
    label: "검증 중",
    bg: "bg-amber-100",
    text: "text-amber-500",
  },
  completed: {
    label: "완성된 프로젝트",
    bg: "bg-zinc-100",
    text: "text-zinc-500",
  },
  hype: {
    label: "Hype된 프로젝트",
    bg: "bg-violet-100",
    text: "text-violet-500",
    icon: "/seedo/icons/star.svg",
  },
};

export function ChipStatus({ variant }: { variant: ChipVariant }) {
  const { label, bg, text, icon } = VARIANTS[variant];
  return (
    <span
      className={`inline-flex items-center justify-center gap-0.5 rounded-[3px] px-1 py-0.5 ${bg}`}
    >
      {icon && (
        <Image
          src={icon}
          alt=""
          width={8}
          height={8}
          className="size-2"
          aria-hidden
        />
      )}
      <span
        className={`text-[10px] leading-[1.3] font-bold tracking-[-0.25px] ${text}`}
      >
        {label}
      </span>
    </span>
  );
}
