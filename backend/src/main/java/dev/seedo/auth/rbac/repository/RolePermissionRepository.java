package dev.seedo.auth.rbac.repository;

import dev.seedo.auth.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
}
