import { formatRelativeKo } from "@/lib/format";
import type { PostApplicant } from "@/lib/queries/post-applicants";

export function PostApplicantsList({
  applicants,
}: {
  applicants: PostApplicant[];
}) {
  return (
    <section
      aria-label="지원자 명단"
      className="flex flex-col gap-3 rounded-md border border-border bg-card p-4"
    >
      <header className="flex items-baseline justify-between">
        <h2 className="text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
          지원자 {applicants.length}
        </h2>
        <span className="text-xs text-muted-foreground">
          작성자만 볼 수 있습니다
        </span>
      </header>

      {applicants.length === 0 ? (
        <p className="rounded-md border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
          아직 지원자가 없습니다.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {applicants.map((a) => (
            <li
              key={a.id}
              className="flex flex-col gap-1 rounded-md border border-border bg-background px-4 py-3"
            >
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="font-semibold text-foreground">
                  {a.applicantName}
                </span>
                <span className="text-xs text-muted-foreground">
                  {formatRelativeKo(a.appliedAt)}
                </span>
              </div>
              {a.message ? (
                <p className="text-sm leading-[1.5] whitespace-pre-wrap text-muted-foreground">
                  {a.message}
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
