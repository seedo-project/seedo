package dev.seedo.project.infrastructure;

import dev.seedo.project.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 상태 전이 트랜잭션의 표준 진입점. 현재 PR (채택) 은 INSERT 만 하므로 사용처가 없지만,
     * 추후 RECRUITING/COMPLETE 전이 트랜잭션에서 사용한다 — 패턴은 IdeaRepository.findByIdForUpdate 동일.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
