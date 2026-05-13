export type CommentTarget = "idea" | "project" | "post";

export function commentTable(target: CommentTarget) {
  return target === "idea"
    ? "idea_comments"
    : target === "project"
      ? "project_comments"
      : "post_comments";
}

export function commentTargetCol(target: CommentTarget) {
  return target === "idea"
    ? "idea_id"
    : target === "project"
      ? "project_id"
      : "post_id";
}
