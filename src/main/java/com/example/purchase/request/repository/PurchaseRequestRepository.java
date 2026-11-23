package com.example.purchase.request.repository;

import com.example.purchase.request.domain.PurchaseRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long>, PurchaseRequestRepositoryCustom {

    /**
     * 상세 화면용. N:1(기안자, 부서, 거래처)은 fetch join으로 한 번에.
     * 1:N(품목 줄, 첨부)은 여기서 fetch join 하지 않는다:
     * List 컬렉션 두 개를 동시에 fetch join하면 MultipleBagFetchException이 난다. → batch fetch로 읽는다.
     */
    @Query("""
            select r from PurchaseRequest r
            join fetch r.requester
            join fetch r.department
            join fetch r.vendor
            where r.id = :id
            """)
    Optional<PurchaseRequest> findDetail(@Param("id") Long id);

    /**
     * 결재 처리(상신·회수·승인·반려)용.
     * OPTIMISTIC_FORCE_INCREMENT: 요청서 자체가 안 바뀌어도 커밋할 때 version을 +1 한다.
     * 중간 단계 승인은 결재선(자식 행)만 바뀌어서 요청서 version이 안 올라가고,
     * 그러면 동시에 들어온 두 처리가 둘 다 성공해 버린다 (docs/troubleshooting/04).
     *
     * 주의: 여기에 join fetch를 붙이면 안 된다. 락 모드가 fetch join한 Member·Department에도 걸려서
     * 커밋할 때 "cannot force version increment on non-versioned entity"로 터진다 (둘 다 @Version이 없음).
     * 기안자·부서는 필요할 때 지연 로딩으로 읽는다.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select r from PurchaseRequest r where r.id = :id")
    Optional<PurchaseRequest> findForApproval(@Param("id") Long id);

    /** 발주 대기: 최종 승인됐지만 아직 발주 안 한 요청서 (구매팀 발주 화면) */
    @Query(value = """
            select r from PurchaseRequest r
            join fetch r.requester join fetch r.department join fetch r.vendor
            where r.status = com.example.purchase.request.domain.RequestStatus.APPROVED
            order by r.completedAt asc
            """,
            countQuery = "select count(r) from PurchaseRequest r where r.status = com.example.purchase.request.domain.RequestStatus.APPROVED")
    org.springframework.data.domain.Page<PurchaseRequest> findApprovedForOrder(org.springframework.data.domain.Pageable pageable);
}
