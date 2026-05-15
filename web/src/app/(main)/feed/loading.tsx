import { ProjectCardSkeleton } from "@/components/project/project-card-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

export default function FeedLoading() {
  return (
    <main className="px-[100px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-9 w-28 rounded-md" />
      </div>
      <section className="mt-8 grid grid-cols-2 gap-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <ProjectCardSkeleton key={i} />
        ))}
      </section>
    </main>
  );
}
