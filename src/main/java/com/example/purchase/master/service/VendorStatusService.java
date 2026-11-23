package com.example.purchase.master.service;

import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.nts.NtsApiException;
import com.example.purchase.master.nts.NtsBusinessClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 거래처 사업자 상태를 국세청 API로 확인해서 저장한다 (BR-09).
 *
 * <p><b>이 클래스에는 일부러 @Transactional을 붙이지 않았다.</b>
 * 트랜잭션은 시작할 때 DB 커넥션을 하나 잡고 끝날 때 돌려준다. 그 안에서 외부 API를 부르면
 * API가 느린 동안(최대 타임아웃 5초) DB를 쓰지도 않으면서 커넥션을 붙잡고 있게 되고,
 * 동시에 몇 명만 눌러도 커넥션 풀(기본 10개)이 바닥나 다른 화면까지 멈춘다 (docs/troubleshooting/06).
 * <pre>
 *   1) 짧은 읽기 트랜잭션: 사업자번호 읽기      (VendorService)
 *   2) 트랜잭션 밖: 국세청 API 호출             (NtsBusinessClient)
 *   3) 짧은 쓰기 트랜잭션: 결과 저장           (VendorService)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorStatusService {

    private final VendorService vendorService;
    private final NtsBusinessClient ntsClient;

    public boolean isApiEnabled() {
        return ntsClient.isEnabled();
    }

    /** 한 곳 확인. 실패해도 예외를 던지지 않고 "확인 안 됨"으로 저장한 뒤 이유를 돌려준다 */
    public CheckResult refresh(Long vendorId) {
        Vendor vendor = vendorService.get(vendorId);                       // 1) 읽기 (트랜잭션 끝, 커넥션 반납)
        if (!ntsClient.isEnabled()) {
            // 키가 없는 건 API 실패가 아니라 설정 문제. 담당자가 정한 상태를 덮어쓰지 않는다
            return new CheckResult(vendor.getName(), vendor.getStatus(),
                    "국세청 API 키(NTS_SERVICE_KEY)가 없어 확인하지 못했습니다. 상태는 그대로 둡니다.", false);
        }
        CheckResult result;
        try {
            NtsBusinessClient.Result r = ntsClient.fetchStatuses(List.of(vendor.getBizNo())).get(vendor.getBizNo()); // 2)
            result = r == null
                    ? new CheckResult(vendor.getName(), VendorStatus.UNKNOWN, "국세청 응답에 이 사업자번호가 없습니다.", false)
                    : new CheckResult(vendor.getName(), r.status(), r.message(), true);
        } catch (NtsApiException e) {
            result = new CheckResult(vendor.getName(), VendorStatus.UNKNOWN, e.getMessage(), false);
        }
        vendorService.applyStatus(vendorId, result.status(), LocalDateTime.now()); // 3) 쓰기
        return result;
    }

    /**
     * 사용 중인 거래처 전체 확인. 100개씩 나눠 부른다 (API 한 번에 100개까지).
     * 9단계 배치에서 매일 밤 이 메서드를 부른다.
     */
    public Summary refreshAll() {
        if (!ntsClient.isEnabled()) {
            return new Summary(0, 0, 0, 0, "국세청 API 키(NTS_SERVICE_KEY)가 없어 확인하지 않았습니다.");
        }
        List<Vendor> vendors = vendorService.usedVendors();
        int ok = 0, unknown = 0, changed = 0;
        String lastError = null;
        for (int from = 0; from < vendors.size(); from += NtsBusinessClient.MAX_PER_CALL) {
            List<Vendor> chunk = vendors.subList(from, Math.min(from + NtsBusinessClient.MAX_PER_CALL, vendors.size()));
            Map<String, NtsBusinessClient.Result> results;
            try {
                results = ntsClient.fetchStatuses(chunk.stream().map(Vendor::getBizNo).toList());
            } catch (NtsApiException e) {
                results = Map.of();
                lastError = e.getMessage();
            }
            LocalDateTime now = LocalDateTime.now();
            for (Vendor v : chunk) {
                NtsBusinessClient.Result r = results.get(v.getBizNo());
                VendorStatus status = r == null ? VendorStatus.UNKNOWN : r.status();
                if (r != null && r.status() != VendorStatus.UNKNOWN) ok++; else unknown++;
                if (status != v.getStatus()) changed++;
                vendorService.applyStatus(v.getId(), status, now);
            }
        }
        log.info("거래처 상태 일괄 확인: 전체 {}, 확인 {}, 확인 안 됨 {}, 바뀜 {}", vendors.size(), ok, unknown, changed);
        return new Summary(vendors.size(), ok, unknown, changed, lastError);
    }

    /** @param checked 국세청에서 실제로 답을 받았는지 (false면 확인 안 됨으로 저장됨) */
    public record CheckResult(String vendorName, VendorStatus status, String message, boolean checked) {
    }

    public record Summary(int total, int checked, int unknown, int changed, String error) {
    }
}
