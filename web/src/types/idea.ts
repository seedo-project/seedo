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
