export type ChatRole = "USER" | "ASSISTANT";

export type ChatMessage = {
  id: string;
  role: ChatRole;
  content: string;
};
