/** Spring ApiResponseAdvice 봉투 — 모든 응답이 이 형태. */
export type ApiResponse<T> =
  | { status: "OK"; message: null; data: T }
  | { status: "ERROR"; message: string; data: null };

/** POST /chat-sessions — 백엔드 미구현. mock route handler 가 반환. */
export type StartChatSessionResponse = {
  sessionId: number;
  createdAt: string;
};

/** POST /chat-sessions/{id}/messages 요청. */
export type SendChatMessageRequest = {
  content: string;
};

/** POST /chat-sessions/{id}/messages 응답. */
export type SendChatMessageResponse = {
  assistantMessageId: number;
  content: string;
  createdAt: string;
};

/** POST /chat-sessions/{id}/finalize 응답. */
export type FinalizeChatSessionResponse = {
  ideaId: number;
  documentId: number;
  version: number;
};
