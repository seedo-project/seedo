/** 백엔드 Idea 도메인 DTO. dev.seedo.idea.web 참조. */

/** POST /ideas/{id}/versions 요청. */
export type PublishIdeaVersionRequest = {
  title: string;
  contentMd: string;
};

/** POST /ideas/{id}/versions 응답. */
export type PublishIdeaVersionResponse = {
  ideaId: number;
  documentId: number;
  version: number;
};

/**
 * GET /ideas/search 응답 한 행. 본문/제목 비포함 — 카드는 keywords 칩 + 가격으로 노출.
 * score 는 RRF 결합 점수 — 정렬에만 사용 (절대값 해석 금지).
 */
export type SearchIdeasResponse = {
  ideaId: number;
  authorId: string;
  currentVersionId: number;
  priceCredits: number;
  rewardCredits: number;
  score: number;
  keywords: string[];
};
