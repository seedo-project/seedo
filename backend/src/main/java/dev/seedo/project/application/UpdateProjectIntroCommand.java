package dev.seedo.project.application;

/**
 * 부분 수정 명령 (#140). 각 필드는 nullable — null 이면 기존 값 보존, 채워져 있으면 덮어쓴다.
 *
 * <p>"비우기" 의미가 필요해지면 별도 sentinel (예: 빈 문자열) 처리를 도입하지만, 현재 흐름에서는 LEADER 가
 * 한 번 채운 표지를 다시 비울 일이 없으므로 단순 null 시맨틱만 지원.
 */
public record UpdateProjectIntroCommand(
        String coverImageUrl,
        String title,
        String description,
        String guideMd
) {
}
