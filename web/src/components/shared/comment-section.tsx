"use client";

import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/auth-context";
import { formatRelativeKo } from "@/lib/format";
import type { CommentItem, CommentTarget } from "@/lib/queries/comments";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

function tableFor(target: CommentTarget) {
  return target === "idea" ? "idea_comments" : "project_comments";
}

function targetColFor(target: CommentTarget) {
  return target === "idea" ? "idea_id" : "project_id";
}

const MAX_LEN = 2000;

export function CommentSection({
  target,
  targetId,
  initialComments,
}: {
  target: CommentTarget;
  targetId: number;
  initialComments: CommentItem[];
}) {
  const { user } = useAuth();
  const [comments, setComments] = useState(initialComments);
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState("");
  const [, startTransition] = useTransition();
  const [busy, setBusy] = useState(false);

  const supabase = createClient();
  const table = tableFor(target);
  const targetCol = targetColFor(target);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const content = draft.trim();
    if (!content) return;
    if (!user) {
      toast.error("로그인이 필요합니다");
      return;
    }
    setBusy(true);
    startTransition(async () => {
      const { data, error } = await supabase
        .from(table)
        .insert({
          author_id: user.id,
          [targetCol]: targetId,
          content,
        })
        .select("id, created_at, updated_at")
        .single();

      if (error || !data) {
        toast.error("댓글 작성에 실패했습니다");
      } else {
        setComments((prev) => [
          ...prev,
          {
            id: Number(data.id),
            authorId: user.id,
            authorName: user.displayName ?? user.nickname,
            content,
            createdAt: data.created_at,
            updatedAt: data.updated_at,
            isAuthor: true,
          },
        ]);
        setDraft("");
      }
      setBusy(false);
    });
  };

  const onStartEdit = (c: CommentItem) => {
    setEditingId(c.id);
    setEditDraft(c.content);
  };

  const onSaveEdit = (c: CommentItem) => {
    if (busy) return;
    const content = editDraft.trim();
    if (!content) return;
    setBusy(true);
    startTransition(async () => {
      const { error } = await supabase
        .from(table)
        .update({ content })
        .eq("id", c.id);
      if (error) {
        toast.error("댓글 수정에 실패했습니다");
      } else {
        setComments((prev) =>
          prev.map((x) => (x.id === c.id ? { ...x, content } : x)),
        );
        setEditingId(null);
      }
      setBusy(false);
    });
  };

  const onDelete = (c: CommentItem) => {
    if (busy) return;
    if (!confirm("이 댓글을 삭제할까요?")) return;
    setBusy(true);
    const prev = comments;
    setComments((cur) => cur.filter((x) => x.id !== c.id));
    startTransition(async () => {
      const { error } = await supabase
        .from(table)
        .update({ deleted_at: new Date().toISOString() })
        .eq("id", c.id);
      if (error) {
        setComments(prev);
        toast.error("댓글 삭제에 실패했습니다");
      }
      setBusy(false);
    });
  };

  return (
    <section className="flex flex-col gap-4" aria-label="댓글">
      <h2 className="text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
        댓글 {comments.length}
      </h2>

      <ul className="flex flex-col gap-3">
        {comments.length === 0 ? (
          <li className="rounded-md border border-border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
            첫 댓글을 남겨보세요.
          </li>
        ) : (
          comments.map((c) => (
            <li
              key={c.id}
              className="rounded-md border border-border bg-card px-4 py-3"
            >
              <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                <span className="font-semibold text-foreground">
                  {c.authorName}
                </span>
                <span>{formatRelativeKo(c.createdAt)}</span>
              </div>
              {editingId === c.id ? (
                <div className="mt-2 flex flex-col gap-2">
                  <textarea
                    value={editDraft}
                    onChange={(e) => setEditDraft(e.target.value)}
                    maxLength={MAX_LEN}
                    rows={3}
                    className="w-full resize-none rounded-md border border-border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                  <div className="flex justify-end gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => setEditingId(null)}
                      disabled={busy}
                    >
                      취소
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => onSaveEdit(c)}
                      disabled={busy || !editDraft.trim()}
                    >
                      저장
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  <p className="mt-1 text-sm leading-[1.5] whitespace-pre-wrap text-foreground">
                    {c.content}
                  </p>
                  {c.isAuthor && (
                    <div className="mt-2 flex justify-end gap-3 text-xs">
                      <button
                        type="button"
                        onClick={() => onStartEdit(c)}
                        className="cursor-pointer text-muted-foreground hover:text-foreground"
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        onClick={() => onDelete(c)}
                        className="cursor-pointer text-muted-foreground hover:text-destructive"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </>
              )}
            </li>
          ))
        )}
      </ul>

      {user ? (
        <form onSubmit={onSubmit} className="flex flex-col gap-2">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={MAX_LEN}
            rows={3}
            placeholder="댓글을 입력하세요"
            className="w-full resize-none rounded-md border border-border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              {draft.length} / {MAX_LEN}
            </span>
            <Button
              type="submit"
              size="sm"
              disabled={busy || !draft.trim()}
            >
              등록
            </Button>
          </div>
        </form>
      ) : (
        <p className="rounded-md border border-dashed border-border px-4 py-3 text-center text-sm text-muted-foreground">
          댓글을 작성하려면 로그인이 필요합니다.
        </p>
      )}
    </section>
  );
}
