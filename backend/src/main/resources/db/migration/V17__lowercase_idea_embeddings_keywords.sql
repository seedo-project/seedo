-- =====================================================================
-- V17: idea_embeddings.keywords lower-case backfill (#147)
-- 의존: V2 (idea_embeddings)
--
-- 배경: V16 까지 keywords 는 LLM 응답 그대로 (대소문자 보존) 저장됐는데, 검색 측
--      SearchIdeasService#tokenize 가 쿼리를 lower-case 화하므로 영어 키워드 매칭이 어긋났다 (#138 follow-up).
--      저장 측 정규화를 IdeaEmbeddingRepositoryImpl 에 추가하면서 (이번 PR), 기존 데이터도 같은 규칙으로
--      backfill.
--
-- 정규화 규칙 (Java IdeaEmbeddingRepositoryImpl#normalizeKeywords 와 동일):
--   - lower-case (Locale.ROOT 와 호환 — PG 의 lower() 도 같은 동작)
--   - trim (공백 제거)
--   - 빈 문자열 제외
--   - 중복 제거 — **첫 등장 순서 보존** (Java 의 LinkedHashSet 의미론). LLM 이 추천한 키워드 순서는
--     사용자 카드 칩 노출 순서로 그대로 쓰이므로, backfill 이 그 순서를 뒤바꾸면 노출이 흔들린다.
--
-- 멱등성: 이미 정규화된 행에 재실행해도 결과 동일. MVP 운영 데이터 규모 (소수~수십 행) 라 단일
--        UPDATE 로 충분 — 락 부담 없음.
-- =====================================================================

UPDATE idea_embeddings
SET keywords = COALESCE(
        -- WITH ORDINALITY 로 입력 인덱스를 보존하고, 정규화 후 MIN(ord) 로 "첫 등장" 시점을 잡아 그 순서대로
        -- 정렬. SELECT DISTINCT 만 쓰면 PG 가 hash/sort 로 처리해 결과 순서가 비결정적이라 LinkedHashSet
        -- 의미론과 어긋난다 (CodeRabbit #148).
        ARRAY(
            SELECT d.normalized
            FROM (
                SELECT btrim(lower(t.k)) AS normalized,
                       MIN(t.ord)        AS first_ord
                FROM unnest(keywords) WITH ORDINALITY AS t(k, ord)
                WHERE btrim(t.k) <> ''
                GROUP BY btrim(lower(t.k))
            ) d
            ORDER BY d.first_ord
        ),
        '{}'::text[]
    )
WHERE keywords IS NOT NULL
  AND cardinality(keywords) > 0;
