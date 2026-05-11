package dev.seedo.project.application;

/**
 * 채택 결과. {@code rewardPaid=false} 는 자가 채택 또는 같은 아이디어의 두 번째 외부 채택자
 * (CLAUDE.md §12 "보상은 첫 채택자만") — 두 경우 모두 트랜잭션은 정상 커밋, 프로젝트는 생성됨.
 * 보상이 지급된 경우만 {@code rewardTransactionId} 가 채워진다.
 */
public record AdoptResult(
        long projectId,
        boolean rewardPaid,
        Long rewardTransactionId
) {
}
