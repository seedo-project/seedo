package dev.seedo.credit.repository;

import dev.seedo.credit.entity.UserCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserCreditRepository extends JpaRepository<UserCredit, UUID> {
}
