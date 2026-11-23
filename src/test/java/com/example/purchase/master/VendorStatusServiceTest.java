package com.example.purchase.master;

import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.nts.NtsApiException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 상태 확인 → 저장 흐름. 국세청 클라이언트는 가짜(@MockBean).
 * VendorStatusService는 트랜잭션 없이 "읽기 → API → 쓰기"를 나눠 하므로 테스트도 @Transactional 없이 실제로 커밋한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class VendorStatusServiceTest {

    @Autowired VendorStatusService statusService;
    @Autowired VendorRepository vendorRepository;
    @MockBean NtsBusinessClient ntsClient;

    Vendor vendor;

    @BeforeEach
    void setUp() {
        vendor = new Vendor("1018100001", "(주)오피스몰", "김오피");
        vendor.changeStatus(VendorStatus.ACTIVE, LocalDateTime.now().minusDays(30));
        vendorRepository.save(vendor);
        when(ntsClient.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        vendorRepository.deleteAll();
    }

    @Test
    @DisplayName("국세청이 폐업이라고 하면 폐업으로 저장 → 새 요청서에서 못 고른다")
    void closed() {
        when(ntsClient.fetchStatuses(List.of("1018100001"))).thenReturn(Map.of("1018100001",
                new NtsBusinessClient.Result("1018100001", VendorStatus.CLOSED, "폐업자")));

        VendorStatusService.CheckResult r = statusService.refresh(vendor.getId());

        assertThat(r.checked()).isTrue();
        Vendor saved = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(VendorStatus.CLOSED);
        assertThat(saved.isSelectable()).isFalse();
        assertThat(saved.getStatusCheckedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("BR-09: API가 실패하면 '확인 안 됨'으로 저장하고 이유를 돌려준다 (예외로 화면이 깨지지 않음)")
    void apiFailure() {
        when(ntsClient.fetchStatuses(anyList())).thenThrow(new NtsApiException("국세청 API를 부르지 못했습니다: ResourceAccessException"));

        VendorStatusService.CheckResult r = statusService.refresh(vendor.getId());

        assertThat(r.checked()).isFalse();
        assertThat(r.message()).contains("ResourceAccessException");
        assertThat(vendorRepository.findById(vendor.getId()).orElseThrow().getStatus()).isEqualTo(VendorStatus.UNKNOWN);
    }

    @Test
    @DisplayName("키가 없으면 부르지도 저장하지도 않는다 (담당자가 정한 상태 유지)")
    void noKey() {
        when(ntsClient.isEnabled()).thenReturn(false);

        VendorStatusService.CheckResult r = statusService.refresh(vendor.getId());

        assertThat(r.message()).contains("NTS_SERVICE_KEY");
        verify(ntsClient, never()).fetchStatuses(anyList());
        assertThat(vendorRepository.findById(vendor.getId()).orElseThrow().getStatus()).isEqualTo(VendorStatus.ACTIVE);
    }

    @Test
    @DisplayName("전체 확인: 거래처 150곳이면 100 + 50으로 두 번 나눠 부른다")
    void refreshAllInChunks() {
        vendorRepository.saveAll(IntStream.rangeClosed(2, 150)
                .mapToObj(i -> new Vendor(String.format("10181%05d", i), "거래처" + i, "대표" + i)).toList());
        when(ntsClient.fetchStatuses(anyList())).thenAnswer(inv -> {
            List<String> bizNos = inv.getArgument(0);
            return bizNos.stream().collect(Collectors.toMap(b -> b,
                    b -> new NtsBusinessClient.Result(b, VendorStatus.ACTIVE, "계속사업자")));
        });

        VendorStatusService.Summary s = statusService.refreshAll();

        assertThat(s.total()).isEqualTo(150);
        assertThat(s.checked()).isEqualTo(150);
        assertThat(s.changed()).isEqualTo(149); // 처음 1곳만 이미 정상이었다
        verify(ntsClient, times(2)).fetchStatuses(anyList());
    }
}
