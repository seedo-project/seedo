"use client";

import { MessageSquare } from "lucide-react";
import { useState } from "react";

import { EmptyState } from "@/components/shared/empty-state";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/auth-context";
import { toast } from "@/lib/toast";

export type PostComment = {
  id: string;
  authorName: string;
  body: string;
  createdAt: string;
};

// TODO: Supabase post_comments 연동. 지금은 mock — 메모리 state만 유지.
export function PostComments({ initial }: { initial: PostComment[] }) {
  const { user } = useAuth();
  const [comments, setComments] = useState<PostComment[]>(initial);
  const [draft, setDraft] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }
    setSubmitting(true);
    setComments((prev) => [
      ...prev,
      {
        id: `local-${Date.now()}`,
        authorName: user.nickname,
        body,
        createdAt: "방금 전",
      },
    ]);
    setDraft("");
    setSubmitting(false);
    toast.success("댓글을 작성했습니다");
  };

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-foreground">
        댓글 {comments.length}
      </h2>

      <form onSubmit={submit} className="flex flex-col gap-2">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={user ? "댓글을 작성하세요" : "로그인 후 작성 가능합니다"}
          aria-label="댓글 입력"
          rows={3}
          disabled={!user}
          className="w-full resize-none rounded-md border border-border bg-card px-3 py-2 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none disabled:cursor-not-allowed disabled:bg-muted"
        />
        <div className="flex justify-end">
          <Button
            type="submit"
            size="sm"
            disabled={!draft.trim() || submitting || !user}
          >
            등록
          </Button>
        </div>
      </form>

      {comments.length === 0 ? (
        <EmptyState
          icon={MessageSquare}
          title="첫 댓글을 남겨보세요"
          className="py-10"
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {comments.map((c) => (
            <li
              key={c.id}
              className="flex flex-col gap-1 rounded-md border border-border bg-card px-4 py-3"
            >
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">
                  {c.authorName}
                </span>
                <span aria-hidden className="h-2 w-px bg-border" />
                <span>{c.createdAt}</span>
              </div>
              <p className="text-sm leading-[1.5] tracking-[-0.35px] whitespace-pre-line text-foreground">
                {c.body}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
