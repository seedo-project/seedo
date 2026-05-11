package dev.seedo.idea.infrastructure;

import dev.seedo.idea.domain.IdeaChatSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdeaChatSessionRepository extends JpaRepository<IdeaChatSession, Long> {

    /**
     * finalize 트랜잭션의 표준 진입점. SELECT FOR UPDATE 로 세션 row 락을 잡아 같은 세션 ID 로 들어온
     * 동시 finalize 호출을 직렬화한다 (CLAUDE.md §8.4).
     * <p>{@code findById} 는 락 없이 캐시 가능하므로 두 동시 호출이 모두 IN_PROGRESS 검증을 통과해
     * 고아 idea/idea_documents 가 생기는 경로가 열린다 — 그래서 finalize 는 반드시 이 메서드 사용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdeaChatSession s where s.id = :id")
    Optional<IdeaChatSession> findByIdForUpdate(@Param("id") Long id);
}
