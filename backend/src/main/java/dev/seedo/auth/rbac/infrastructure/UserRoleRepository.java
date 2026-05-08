package dev.seedo.auth.rbac.infrastructure;

import dev.seedo.auth.rbac.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
}
