package dev.seedo.credit.infrastructure;

import dev.seedo.credit.domain.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
}
