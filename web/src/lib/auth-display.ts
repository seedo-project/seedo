/** UI 표시용 사용자 이름. 가입 시 입력한 name → 이메일 prefix → nickname(UUID fallback) 순. */
export function resolveDisplayName(
  metaName: unknown,
  email: string | null,
  nickname: string,
): string {
  if (typeof metaName === "string" && metaName.trim()) return metaName.trim();
  if (email) {
    const prefix = email.split("@")[0];
    if (prefix) return prefix;
  }
  return nickname;
}
