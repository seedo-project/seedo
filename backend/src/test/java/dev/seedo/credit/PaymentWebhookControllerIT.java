package dev.seedo.credit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.seedo.credit.infrastructure.UserCreditRepository;
import dev.seedo.support.AbstractIntegrationTest;
import dev.seedo.support.UserFixture;
import dev.seedo.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 레이어 통합 — webhook 시크릿 가드, 멱등성 응답 분기, 실패 status 무시 흐름이 한 줄로 흐르는지 검증.
 *
 * <p>동시성 + 같은 paymentId 동시 호출 케이스는 {@link ChargeCreditServiceIT#concurrent_calls_with_same_reference_yield_one_real_one_skip}
 * 가 이미 검증한다 — controller 레벨에서 중복하지 않는다.
 *
 * <p>JwtDecoder 모킹이 없는 이유: webhook 경로는 SecurityConfig 가 permitAll 처리하므로 JWT 검증 자체가
 * 안 일어난다.
 *
 * <p>클래스 레벨 {@code @Transactional} 을 쓰지 않는 이유: MockMvc 는 같은 JVM 의 dispatcher 를 호출해
 * 테스트 트랜잭션을 그대로 join 한다 (propagation REQUIRED). 그 상태에서 같은 {@code paymentId} 로
 * 두 번 호출하면 두 번째가 1st-level cache 로 첫 INSERT 를 보고 idempotent skip — 운영에서는 첫 트랜잭션
 * commit 후 두 번째 별개 트랜잭션이 DB UNIQUE 가드로 잡아내는 흐름과 메커니즘이 다르다.
 * {@link ChargeCreditServiceIT} 와 동일하게 매 테스트가 새 UUID 로 사용자/{@code paymentId} 를 만들어
 * row-level 로 격리한다.
 */
@AutoConfigureMockMvc
class PaymentWebhookControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/webhooks/payments/portone";
    private static final String SECRET_HEADER = "X-Webhook-Secret";
    /** test/resources/application.yml 의 payment.webhook.secret 와 일치해야 한다. */
    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private UserCreditRepository creditRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void same_paymentId_twice_charges_once() throws Exception {
        UUID userId = setupUser();
        String paymentId = "pmt-" + UUID.randomUUID();
        String payload = json(userId, paymentId, 100L, "PAID");

        mockMvc.perform(post(PATH)
                        .header(SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.result").value("CHARGED"))
                .andExpect(jsonPath("$.data.balance").value(100))
                .andExpect(jsonPath("$.data.transactionId").isNumber());

        mockMvc.perform(post(PATH)
                        .header(SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DUPLICATE"))
                .andExpect(jsonPath("$.data.balance").value(100));

        assertThat(creditRepo.findById(userId).orElseThrow().getBalance()).isEqualTo(100L);
    }

    @Test
    void missing_secret_header_returns_401_and_does_not_charge() throws Exception {
        UUID userId = setupUser();

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userId, "pmt-no-header", 100L, "PAID")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").exists());

        assertThat(creditRepo.findById(userId).orElseThrow().getBalance()).isZero();
    }

    @Test
    void wrong_secret_returns_401_and_does_not_charge() throws Exception {
        UUID userId = setupUser();

        mockMvc.perform(post(PATH)
                        .header(SECRET_HEADER, "WRONG_SECRET")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userId, "pmt-wrong", 100L, "PAID")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));

        assertThat(creditRepo.findById(userId).orElseThrow().getBalance()).isZero();
    }

    @Test
    void non_paid_status_is_acked_without_charging() throws Exception {
        UUID userId = setupUser();

        mockMvc.perform(post(PATH)
                        .header(SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userId, "pmt-failed", 100L, "FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("IGNORED"))
                .andExpect(jsonPath("$.data.balance").doesNotExist())
                .andExpect(jsonPath("$.data.transactionId").doesNotExist());

        assertThat(creditRepo.findById(userId).orElseThrow().getBalance()).isZero();
    }

    private UUID setupUser() {
        UUID id = UserFixture.create(userRepo);
        UserFixture.grantCredit(creditRepo, id, 0L);
        return id;
    }

    private String json(UUID userId, String paymentId, long amount, String status) throws Exception {
        // LinkedHashMap 으로 직렬화 순서 고정 — 디버그 시 페이로드 비교가 쉽다.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentId", paymentId);
        body.put("userId", userId.toString());
        body.put("amount", amount);
        body.put("status", status);
        return objectMapper.writeValueAsString(body);
    }
}
