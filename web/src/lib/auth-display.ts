/**
 * UI 표시용 사용자 이름. 닉네임 우선 노출.
 *
 * 우선순위:
 *   1. nickname (UUID fallback `u-...` 패턴이 아니면)
 *   2. metadata.name (이름 — 기존 사용자 호환)
 *   3. 이메일 prefix
 *   4. nickname (UUID fallback) — 최후의 수단
 *
 * V3 트리거가 만든 fallback nickname (`u-<uuid_hex>`) 은 사용자가 입력한 게 아니므로
 * 노출 1순위에서 배제. 프로필 편집 UI 가 생기면 사용자가 직접 설정해 정상화 가능.
 */
const UUID_FALLBACK = /^u-[0-9a-f]{32}$/;

export function resolveDisplayName(
  metaName: unknown,
  email: string | null,
  nickname: string,
): string {
  const hasRealNickname = nickname && !UUID_FALLBACK.test(nickname);
  if (hasRealNickname) return nickname;
  if (typeof metaName === "string" && metaName.trim()) return metaName.trim();
  if (email) {
    const prefix = email.split("@")[0];
    if (prefix) return prefix;
  }
  return nickname;
}
