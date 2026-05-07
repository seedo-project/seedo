package dev.seedo.auth.rbac.repository;

import dev.seedo.auth.rbac.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
}
