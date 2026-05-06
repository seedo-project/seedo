import Image from "next/image";

import { ChipStatus, type ChipVariant } from "./chip-status";

export type Project = {
  id: string;
  title: string;
  subtitle: string;
  description: string;
  thumbnailUrl?: string | null;
  statuses: ChipVariant[];
};

export function ProjectCard({ project }: { project: Project }) {
  return (
    <article className="flex items-center gap-5 py-3 pr-5">
      <div className="relative flex size-32 shrink-0 items-end justify-end rounded-lg bg-zinc-100 px-1.5 py-2">
        {project.thumbnailUrl && (
          <Image
            src={project.thumbnailUrl}
            alt=""
            fill
            sizes="128px"
            className="rounded-lg object-cover"
          />
        )}
        <button
          type="button"
          aria-label="북마크"
          className="relative size-6"
        >
          <Image
            src="/seedo/icons/bookmark.svg"
            alt=""
            fill
            sizes="24px"
            className="object-contain"
          />
        </button>
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start gap-1.5">
          {project.statuses.map((s) => (
            <ChipStatus key={s} variant={s} />
          ))}
        </div>
        <h3 className="line-clamp-1 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
          {project.title}
        </h3>
        <p className="line-clamp-1 text-[11px] leading-[1.5] font-normal tracking-[-0.275px] text-[#a1a1aa]">
          {project.subtitle}
        </p>
        <p className="line-clamp-3 h-[54px] text-xs leading-[1.5] font-medium tracking-[-0.3px] text-muted-foreground">
          {project.description}
        </p>
      </div>
    </article>
  );
}
