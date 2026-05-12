package dev.seedo.idea.web;

import dev.seedo.idea.application.IdeaSearchResult;
import dev.seedo.idea.application.SearchIdeasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 자연어 검색 — 의미 기반 유사도 정렬. 본문은 비포함 (구매자만 RLS 로 열람).
 *
 * <p>limit 은 service 에서 [1, 50] 클램프. 클라이언트가 큰 값을 보내도 200 + 잘려서 반환.
 */
@RestController
@RequestMapping("/ideas")
@Tag(name = "아이디어 검색", description = "자연어 쿼리로 의미상 가까운 아이디어를 정렬해 반환.")
public class IdeaSearchController {

    private final SearchIdeasService searchService;

    public IdeaSearchController(SearchIdeasService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @Operation(
            summary = "아이디어 자연어 검색",
            description = """
                    쿼리 텍스트를 임베딩(text-embedding-3-small, 1536D) 으로 변환해 cosine 거리가
                    가까운 PUBLISHED 아이디어를 정렬해 돌려준다.

                    - 임베딩이 없는 아이디어는 결과에서 자연 배제 (INNER JOIN)
                    - DELETED / DRAFT / ARCHIVED 는 노출 안 됨
                    - similarity 는 1 - cosine_distance — 1 에 가까울수록 유사
                    - 본문은 비포함 — 본문은 구매 후 별도 경로 (RLS + Spring 모두 일관)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공. 가장 가까운 결과부터 정렬된 메타 리스트."),
            @ApiResponse(responseCode = "400", description = "쿼리가 비어있거나 공백뿐", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT 누락 또는 만료", content = @Content)
    })
    public List<IdeaSearchResult> search(
            @Parameter(description = "검색 자연어 쿼리. 공백만 있으면 400.", required = true, example = "공부 습관 관리 앱")
            @RequestParam("q") String query,
            @Parameter(description = "최대 결과 수 (기본 20, 최대 50). 범위 밖 값은 자동 클램프.", example = "20")
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return searchService.search(query, limit);
    }
}
