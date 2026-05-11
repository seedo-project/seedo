package dev.seedo.project.web;

import dev.seedo.common.web.CurrentUserId;
import dev.seedo.project.application.AdoptCommand;
import dev.seedo.project.application.AdoptIdeaService;
import dev.seedo.project.application.AdoptResult;
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
public class AdoptIdeaController {

    private final AdoptIdeaService service;

    public AdoptIdeaController(AdoptIdeaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasAuthority('PERM_PROJECT_CREATE')")
    public AdoptResponse adopt(@PathVariable("id") Long ideaId, @CurrentUserId UUID userId) {
        AdoptResult result = service.adopt(new AdoptCommand(ideaId, userId));
        return new AdoptResponse(result.projectId(), result.rewardPaid(), result.rewardTransactionId());
    }
}
