/** 백엔드 Project 도메인 DTO. dev.seedo.project.web 참조. */

export type ProjectStatus =
  | "DRAFT"
  | "RECRUITING"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "ARCHIVED"
  | "DELETED";

/** PATCH /projects/{id}/intro 요청. 모든 필드 optional — null/미설정은 기존 값 보존. */
export type UpdateProjectIntroRequest = {
  coverImageUrl?: string | null;
  title?: string | null;
  description?: string | null;
  guideMd?: string | null;
};

/** PATCH /projects/{id}/intro, POST /projects/{id}/publish 응답. */
export type ProjectIntroResponse = {
  projectId: number;
  status: ProjectStatus;
  coverImageUrl: string | null;
  title: string | null;
  description: string | null;
  guideMd: string | null;
};
