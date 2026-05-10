package dev.seedo.support;

import dev.seedo.credit.domain.UserCredit;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.user.domain.User;
import dev.seedo.user.infrastructure.UserRepository;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * IT 에서 사용자 / 권한 / 잔액 row 셋업을 표현하는 정적 헬퍼.
 *
 * <p>Spring 빈 대신 정적 메서드를 쓰는 이유: 호출자(IT)의 트랜잭션 컨텍스트를 따라가야 하기 때문.
 * IT 가 {@code @Transactional} 이면 fixture 호출도 같은 트랜잭션, 아니면 각자 saveAndFlush 가 즉시 커밋된다.
 */
public final class UserFixture {

    private UserFixture() {
    }

    /** 기본 사용자 row 1 개 생성 후 UUID 반환. nickname/email 은 UUID 기반 임의값. */
    public static UUID create(UserRepository userRepo) {
        UUID id = UUID.randomUUID();
        userRepo.saveAndFlush(new User(id, "u-" + id + "@test", "n-" + id.toString().substring(0, 8)));
        return id;
    }

    /** 사용자 + 지정한 role 매핑까지 생성. role_id 는 V1 시드 (1=USER, 2=ADMIN). */
    public static UUID createWithRole(UserRepository userRepo, EntityManager em, long roleId) {
        UUID id = create(userRepo);
        grantRole(em, id, roleId);
        return id;
    }

    /** 기존 사용자에게 role 매핑 추가. UserRole JPA 엔티티 거치지 않고 native 로 직접 INSERT. */
    public static void grantRole(EntityManager em, UUID userId, long roleId) {
        em.createNativeQuery(
                        "INSERT INTO user_roles(user_id, role_id) VALUES (CAST(:uid AS uuid), :rid)")
                .setParameter("uid", userId.toString())
                .setParameter("rid", roleId)
                .executeUpdate();
        em.flush();
    }

    /** user_credits row 생성. balance &gt; 0 이면 그만큼 충전된 상태. */
    public static void grantCredit(UserCreditRepository creditRepo, UUID userId, long balance) {
        UserCredit credit = new UserCredit(userId);
        if (balance > 0) {
            credit.applyDelta(balance);
        }
        creditRepo.saveAndFlush(credit);
    }
}
