package dev.seedo.idea.application;

import dev.seedo.idea.application.port.out.EmbeddingClient;
import dev.seedo.idea.infrastructure.IdeaEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 자연어 쿼리를 두 신호 — (1) 의미 유사도 (text-embedding-3-small + pgvector cosine),
 * (2) 키워드 매칭 (쿼리 토큰 ↔ {@code idea_embeddings.keywords} GIN overlap) — 로 동시 검색한 뒤
 * <b>RRF (Reciprocal Rank Fusion, k=60)</b> 로 한 점수에 합쳐 정렬한다 (이슈 #138).
 *
 * <p>RRF 공식: 각 결과 doc d 에 대해 {@code score(d) = Σ 1 / (k + rank_i(d))}. 두 리스트에 모두 들어
 * 있으면 더 위로, 한 쪽에만 들어 있어도 살아남는다. 절대값은 해석하지 않고 정렬 순서만 사용
 * (Cormack et al. 2009, k=60 표준).
 *
 * <p><b>Graceful degradation</b>: 임베딩 호출 (OpenAI) 이 실패해도 키워드 매칭 결과만으로 응답한다.
 * 임베딩은 OpenAI 의 외부 의존이라 일시적 장애 / 레이트 리밋이 흔하다 — 검색 자체가 죽으면 사용자
 * 흐름 전체가 막히므로, 한 신호가 빠져도 다른 신호로 돌아가게 한다.
 *
 * <p><b>토크나이저</b>: MVP 라 한국어 형태소 분석기 없이 공백 / 구두점 기준 단순 분리 + lower-case
 * (이슈 #138 의 "이번에 안 하는 것"). LLM 이 finalize 시 박는 키워드도 짧은 한국어 명사라 사용자가
 * 평이하게 쓴 쿼리와 부분 매칭이 잘 일어난다.
 *
 * <p>의도적으로 메서드에 {@code @Transactional} 을 붙이지 않는다 (CLAUDE.md backend §"외부 호출").
 * 외부 LLM 호출은 응답까지 수백 ms ~ 수 초 — 그동안 DB 커넥션을 점유하면 동시 검색이 늘 때 커넥션
 * 풀이 고갈된다. 임베딩 호출은 풀 밖에서 끝내고, 두 native query 만 각각 짧은 트랜잭션 (Hibernate
 * 자동) 으로 실행한다.
 */
@Service
public class SearchIdeasService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    /** RRF 표준 상수. 너무 작으면 1위 가중치 폭발, 너무 크면 순위 차이가 평탄해진다. 60 이 관례. */
    static final int RRF_K = 60;
    /** 후보 풀 — 각 리스트는 최종 limit 만큼만 보면 동률 / 한쪽 매칭에서 다양성이 줄어든다. */
    static final int CANDIDATE_LIMIT = MAX_LIMIT;

    private static final Logger log = LoggerFactory.getLogger(SearchIdeasService.class);

    private final EmbeddingClient embeddingClient;
    private final IdeaEmbeddingRepository embeddingRepo;

    public SearchIdeasService(EmbeddingClient embeddingClient, IdeaEmbeddingRepository embeddingRepo) {
        this.embeddingClient = embeddingClient;
        this.embeddingRepo = embeddingRepo;
    }

    public List<IdeaSearchResult> search(String query, Integer requestedLimit) {
        if (query == null || query.isBlank()) {
            throw new SearchQueryEmptyException();
        }
        int limit = clampLimit(requestedLimit);
        String trimmed = query.trim();

        List<IdeaSearchResult> embeddingHits = safeEmbeddingSearch(trimmed);
        List<IdeaSearchResult> keywordHits = keywordSearch(trimmed);

        return fuseRrf(embeddingHits, keywordHits, limit);
    }

    /**
     * 임베딩 호출 + native 검색. 임베딩 외부 호출 또는 그 후속 DB 호출이 실패하면 빈 리스트로 강등 —
     * 사용자에게는 키워드 결과만으로라도 응답이 가도록 한다. 의도적으로 {@link RuntimeException} 광범위
     * catch — {@link EmbeddingClient#embed} 가 던지는 예외 유형이 어댑터별로 달라 좁히면 새는 게 더 위험.
     */
    private List<IdeaSearchResult> safeEmbeddingSearch(String query) {
        try {
            float[] queryEmbedding = embeddingClient.embed(query);
            return embeddingRepo.searchPublishedByEmbedding(queryEmbedding, CANDIDATE_LIMIT);
        } catch (RuntimeException e) {
            log.warn("embedding search failed; falling back to keyword-only — query=\"{}\"", query, e);
            return List.of();
        }
    }

    private List<IdeaSearchResult> keywordSearch(String query) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        return embeddingRepo.searchPublishedByKeywords(tokens, CANDIDATE_LIMIT);
    }

    /**
     * RRF 결합. 두 리스트의 각 doc 에 {@code 1 / (k + rank)} 를 더해 score 계산, 합산 score 내림차순.
     * 동률은 ideaId 내림차순으로 안정 정렬 — 결과의 결정성을 IT 가 검증할 수 있게.
     *
     * <p>두 리스트 어느 쪽에서든 먼저 등장한 row 의 {@code keywords} / 메타를 그대로 살린다 (둘 다 같은
     * idea_embeddings JOIN 으로 가져온 값이라 동일). {@code score} 만 RRF 합산값으로 덮어쓴다.
     */
    private static List<IdeaSearchResult> fuseRrf(
            List<IdeaSearchResult> a,
            List<IdeaSearchResult> b,
            int limit
    ) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, IdeaSearchResult> seen = new LinkedHashMap<>();

        accumulate(a, scores, seen);
        accumulate(b, scores, seen);

        List<IdeaSearchResult> fused = new ArrayList<>(seen.size());
        for (Map.Entry<Long, IdeaSearchResult> entry : seen.entrySet()) {
            IdeaSearchResult base = entry.getValue();
            fused.add(new IdeaSearchResult(
                    base.ideaId(),
                    base.authorId(),
                    base.currentVersionId(),
                    base.priceCredits(),
                    base.rewardCredits(),
                    scores.get(entry.getKey()),
                    base.keywords()
            ));
        }

        fused.sort(Comparator
                .comparingDouble(IdeaSearchResult::score).reversed()
                .thenComparing(Comparator.comparingLong(IdeaSearchResult::ideaId).reversed()));

        return fused.size() <= limit ? fused : fused.subList(0, limit);
    }

    private static void accumulate(
            List<IdeaSearchResult> list,
            Map<Long, Double> scores,
            Map<Long, IdeaSearchResult> seen
    ) {
        for (int i = 0; i < list.size(); i++) {
            IdeaSearchResult row = list.get(i);
            int rank = i + 1; // RRF rank 는 1-based.
            scores.merge(row.ideaId(), 1.0 / (RRF_K + rank), Double::sum);
            seen.putIfAbsent(row.ideaId(), row);
        }
    }

    /**
     * 공백·구두점 분리 + lower-case + 중복 제거. MVP — 한국어 형태소 분석기 미적용 (이슈 #138).
     * 토큰을 lower-case 로 정규화하므로 keywords 도 finalize 시 lower-case 로 저장돼 있어야 비교가 맞다.
     */
    static List<String> tokenize(String query) {
        String[] raw = query.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String token : raw) {
            if (!token.isBlank()) {
                unique.add(token);
            }
        }
        return new ArrayList<>(unique);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
