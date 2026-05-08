package dev.seedo.auth.rbac.infrastructure;

import dev.seedo.auth.rbac.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
}
