package dev.seedo.project.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.project.application.AdoptCommand;
import dev.seedo.project.application.AdoptIdeaService;
import dev.seedo.project.application.AdoptResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 아이디어 채택 → 프로젝트 생성 (+ 첫 채택자에게 작성자 보상). 실제 매핑 경로는
 * {@code /api/v1/ideas/{id}/adopt} — {@code WebMvcConfig} 가 prefix 부착.
 *
 * <p>RBAC 은 V1 시드의 {@code PROJECT_CREATE} 재사용 — 채택 = 프로젝트 생성. 본인 여부는 service 의
 * authorId 비교가 담당 (자가 채택 분기 — RBAC 외부 리소스 정책).
 */
@RestController
@RequestMapping("/ideas")
@Tag(name = "아이디어 채택", description = "구매한 아이디어를 채택해 프로젝트를 생성한다. 첫 채택자가 호출하면 작성자에게 보상 크레딧이 지급된다.")
public class AdoptIdeaController {

    private final AdoptIdeaService service;

    public AdoptIdeaController(AdoptIdeaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasAuthority('PERM_PROJECT_CREATE')")
    @Operation(
            summary = "아이디어 채택 → 프로젝트 생성",
            description = """
                    PUBLISHED 상태의 아이디어를 채택해 프로젝트를 생성한다.

                    - 채택 전 해당 아이디어 구매가 필수 — 미구매 시 400 (자가 채택은 예외).
                    - 첫 채택자만 작성자에게 보상이 지급된다 (rewards 의 partial UNIQUE 가 보장).
                    - 자가 채택 시 보상은 skip (rewardPaid=false), rewardTransactionId 는 null.
                    - 프로젝트 INSERT + 멤버(LEADER) INSERT + 보상 잔액·원장·rewards row 가 한 트랜잭션에서 처리된다.
                    - idea row 비관적 락(PESSIMISTIC_WRITE)으로 동시 채택 race 차단. 그래도 race 가 빠져나가면
                      partial UNIQUE 에 걸려 409.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "채택 성공. 생성된 projectId 와 보상 정보(rewardPaid, rewardTransactionId) 반환."),
            @ApiResponse(responseCode = "400", description = "구매하지 않은 아이디어 / PUBLISHED 상태가 아닌 아이디어", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content),
            @ApiResponse(responseCode = "403", description = "PERM_PROJECT_CREATE 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "해당 아이디어가 존재하지 않음", content = @Content),
            @ApiResponse(responseCode = "409", description = "이미 다른 사용자가 채택해 보상이 지급됨 (race 가드)", content = @Content)
    })
    public AdoptResponse adopt(
            @Parameter(description = "채택할 아이디어 ID", example = "42", required = true)
            @PathVariable("id") Long ideaId,
            @CurrentUserId UUID userId) {
        AdoptResult result = service.adopt(new AdoptCommand(ideaId, userId));
        return new AdoptResponse(result.projectId(), result.rewardPaid(), result.rewardTransactionId());
    }
}
