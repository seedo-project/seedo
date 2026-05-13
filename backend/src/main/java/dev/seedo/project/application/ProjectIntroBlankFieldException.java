package dev.seedo.project.application;

/**
 * title / description 을 blank ("" 또는 공백) 로 수정하려 시도 — 400 으로 매핑 (CodeRabbit #141).
 *
 * <p>publish 후 IN_PROGRESS 상태에서도 updateIntro 가 호출 가능하므로, blank 입력을 허용하면 한 번 공개된
 * 프로젝트의 필수 필드를 사실상 비우는 우회 경로가 된다. V15 의 CHECK 가드는 NOT NULL 만 보므로 blank
 * 는 통과시키는 hole 이 있었다.
 */
public class ProjectIntroBlankFieldException extends RuntimeException {

    private final Long projectId;
    private final String reason;

    public ProjectIntroBlankFieldException(Long projectId, String reason) {
        super("blank intro field for projectId=" + projectId + ": " + reason);
        this.projectId = projectId;
        this.reason = reason;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getReason() {
        return reason;
    }
}
