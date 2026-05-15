import { IdeaCardSkeletonGrid } from "@/components/idea/idea-card-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

export default function IdeaLoading() {
  return (
    <main className="px-4 md:px-[100px] pt-10 pb-24">
      <div className="flex items-center justify-center gap-3">
        <Skeleton className="h-12 w-full max-w-[610px] rounded-full" />
        <Skeleton className="h-12 w-44 rounded-full" />
      </div>
      <section className="mt-12">
        <IdeaCardSkeletonGrid />
      </section>
    </main>
  );
}
