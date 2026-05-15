import { PostCardSkeletonList } from "@/components/post/post-card-skeleton";
import { Skeleton } from "@/components/ui/skeleton";

export default function BoardLoading() {
  return (
    <main className="px-4 md:px-[100px] pt-8 pb-16">
      <div className="flex w-full items-center justify-between">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-9 w-28 rounded-md" />
      </div>
      <div className="mt-8">
        <PostCardSkeletonList count={6} />
      </div>
    </main>
  );
}
