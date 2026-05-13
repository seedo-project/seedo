-- =====================================================================
-- V17: idea_embeddings.keywords lower-case backfill (#147)
-- 의존: V2 (idea_embeddings)
--
-- 배경: V16 까지 keywords 는 LLM 응답 그대로 (대소문자 보존) 저장됐는데, 검색 측
--      SearchIdeasService#tokenize 가 쿼리를 lower-case 화하므로 영어 키워드 매칭이 어긋났다 (#138 follow-up).
--      저장 측 정규화를 IdeaEmbeddingRepositoryImpl 에 추가하면서 (이번 PR), 기존 데이터도 같은 규칙으로
--      backfill.
--
-- 정규화 규칙 (Java 코드와 동일):
--   - lower-case (Locale.ROOT 와 호환 — PG 의 lower() 도 같은 동작)
--   - trim (공백 제거)
--   - 빈 문자열 제외
--   - 중복 제거 (DISTINCT)
--
-- 멱등성: 이미 정규화된 행에 재실행해도 결과 동일. MVP 운영 데이터 규모 (소수~수십 행) 라 단일
--        UPDATE 로 충분 — 락 부담 없음.
-- =====================================================================

UPDATE idea_embeddings
SET keywords = COALESCE(
        ARRAY(
            SELECT DISTINCT btrim(lower(k))
            FROM unnest(keywords) AS k
            WHERE btrim(k) <> ''
        ),
        '{}'::text[]
    )
WHERE keywords IS NOT NULL
  AND cardinality(keywords) > 0;
