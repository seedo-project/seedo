package dev.seedo.idea.application;

/**
 * 검색 쿼리가 비어있거나 공백뿐 — 400 으로 매핑 ({@link dev.seedo.idea.web.IdeaExceptionHandler}).
 *
 * <p>{@code @NotBlank} 같은 bean validation 으로도 잡을 수 있지만, 쿼리 파라미터는 검증 어노테이션
 * 부착이 record DTO 보다 fragile — service 진입에서 명시적으로 한 번 더 검사한다.
 */
public class SearchQueryEmptyException extends RuntimeException {
    public SearchQueryEmptyException() {
        super("search query must not be blank");
    }
}
