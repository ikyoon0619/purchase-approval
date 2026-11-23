package com.example.purchase.master;

import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.nts.NtsBusinessClient;
import com.example.purchase.master.repository.VendorRepository;
import com.example.purchase.master.service.VendorStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 외부 API를 트랜잭션 안에서 부르면 커넥션 풀이 바닥나는 문제 (docs/troubleshooting/06).
 *
 * <p>커넥션 풀을 2개로 줄이고(실서버 기본 10개), 커넥션을 0.5초까지만 기다리게 했다.
 * 가짜 국세청 API는 "응답이 느린 상황"을 흉내 내려고 테스트가 풀어 줄 때까지 멈춰 있는다.
 * 두 사람이 동시에 [상태 확인]을 누른 상태에서, 세 번째 사람이 아무 화면(DB 조회)이나 열면 어떻게 되는지 본다.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=500"
})
@ActiveProfiles("test")
class ExternalCallConnectionPoolTest {

    @Autowired VendorStatusService statusService;
    @Autowired VendorRepository vendorRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockBean NtsBusinessClient ntsClient;

    TransactionTemplate tx;
    Long vendorId;
    CountDownLatch insideApi;
    CountDownLatch release;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        vendorId = vendorRepository.save(new Vendor("1018100001", "(주)오피스몰", "김오피")).getId();
        insideApi = new CountDownLatch(2);
        release = new CountDownLatch(1);
        when(ntsClient.isEnabled()).thenReturn(true);
        when(ntsClient.fetchStatuses(anyList())).thenAnswer(inv -> {
            insideApi.countDown();                 // "지금 API 응답 기다리는 중"
            release.await(10, TimeUnit.SECONDS);   // 느린 응답
            return Map.of("1018100001", new NtsBusinessClient.Result("1018100001", VendorStatus.ACTIVE, "계속사업자"));
        });
    }

    @AfterEach
    void cleanUp() {
        release.countDown();
        vendorRepository.deleteAll();
    }

    @Test
    @DisplayName("[문제] 트랜잭션 안에서 API를 기다리면, 2명이 커넥션 2개를 붙잡아 3번째 사람의 단순 조회가 실패한다")
    void insideTransaction() throws Exception {
        List<Thread> users = start(2, () -> tx.executeWithoutResult(s -> statusService.refresh(vendorId))); // @Transactional로 감싼 것과 같음
        assertThat(insideApi.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> tx.execute(s -> vendorRepository.count()))
                .isInstanceOf(CannotCreateTransactionException.class)
                .hasRootCauseInstanceOf(SQLTransientConnectionException.class); // HikariPool-1 - Connection is not available, request timed out after 500ms

        release.countDown();
        for (Thread t : users) t.join(5_000);
    }

    @Test
    @DisplayName("[해결] API 호출을 트랜잭션 밖으로 빼면, 2명이 API를 기다리는 동안에도 다른 조회가 바로 된다")
    void outsideTransaction() throws Exception {
        List<Thread> users = start(2, () -> statusService.refresh(vendorId)); // 읽기 → (커넥션 반납) → API → 쓰기
        assertThat(insideApi.await(5, TimeUnit.SECONDS)).isTrue();

        long started = System.nanoTime();
        Long count = tx.execute(s -> vendorRepository.count());
        assertThat(count).isEqualTo(1L);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(500);

        release.countDown();
        for (Thread t : users) t.join(5_000);
        assertThat(vendorRepository.findById(vendorId).orElseThrow().getStatus()).isEqualTo(VendorStatus.ACTIVE);
    }

    private static List<Thread> start(int n, Runnable work) {
        List<Thread> threads = java.util.stream.IntStream.range(0, n).mapToObj(i -> new Thread(work)).toList();
        threads.forEach(Thread::start);
        return threads;
    }
}
