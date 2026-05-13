package dev.seedo.project.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 소개 4 항목 부분 수정 요청 (#140). 모든 필드 optional — null 은 기존 값 보존.
 *
 * <p>길이 검증은 도메인·DB 양쪽에 있지만 (V15 CHECK + JPA @Column length), 400 으로 빠르게 거부하기 위해
 * 컨트롤러에서 Bean Validation 1차 가드.
 */
public record UpdateProjectIntroRequest(

        @Schema(description = "대표 이미지 URL (Supabase Storage public URL). 미설정은 null.", example = "https://...supabase.co/storage/v1/object/public/projects/cover.png")
        @Size(max = 500)
        String coverImageUrl,

        @Schema(description = "프로젝트 제목 — publish 시 필수.", example = "공부 습관 트래커")
        @Size(min = 1, max = 200)
        String title,

        @Schema(description = "프로젝트 설명 (마크다운) — publish 시 필수.", example = "타이머 + 통계로 학습 흐름을 가시화")
        @Size(min = 1, max = 10000)
        String description,

        @Schema(description = "프로젝트 진행 가이드 (마크다운).", example = "## 단계 1...")
        @Size(min = 1, max = 20000)
        String guideMd
) {
}
