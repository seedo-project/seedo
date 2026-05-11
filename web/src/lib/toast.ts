import { toast as sonnerToast } from "sonner";

// 한글 카피 컨벤션: 평서형 종결, 마침표 없음.
// 예: "로그아웃되었습니다", "구매 완료", "준비 중인 기능입니다"

export const toast = {
  success: (message: string) => sonnerToast.success(message),
  error: (message: string) => sonnerToast.error(message),
  info: (message: string) => sonnerToast.info(message),
  warning: (message: string) => sonnerToast.warning(message),
  /** 아직 연결 안 된 기능 mock 클릭 시 */
  notReady: (label = "준비 중인 기능입니다") => sonnerToast.info(label),
};
