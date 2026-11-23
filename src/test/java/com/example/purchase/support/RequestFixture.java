package com.example.purchase.support;

import com.example.purchase.master.domain.Item;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.request.domain.PurchaseRequest;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 요청서 테스트 준비: 직원 2명(개발팀·영업팀), 구매팀 2명(사원, 차장), 품목 3개, 거래처 2곳(정상, 폐업)
 * 6단계 결재자: 개발팀장, 영업팀장, 사업본부장. 경영지원본부·구매팀에는 팀장/부서장이 없다 (결재자 없음 테스트용)
 */
public class RequestFixture {

    public Member dev;
    public Member sales;
    public Member buyer;
    public Member buyLead;
    public Member devLead;
    public Member salesLead;
    public Member bizHead;
    public Item laptop;
    public Item monitor;
    public Item paper;
    public Vendor activeVendor;
    public Vendor closedVendor;

    private final EntityManager em;

    public RequestFixture(EntityManager em) {
        this.em = em;
        OrgFixture f = new OrgFixture(em);
        Department division = f.division("D200", "사업본부");
        Department devTeam = f.team("D210", "개발팀", division);
        Department salesTeam = f.team("D220", "영업팀", division);
        Department buyTeam = f.team("D110", "구매팀", f.division("D100", "경영지원본부"));
        Position p = f.position("사원", 1);
        dev = f.member("dev1", "김개발", devTeam, p, Role.EMPLOYEE);
        sales = f.member("sales1", "정영업", salesTeam, p, Role.EMPLOYEE);
        buyer = f.member("buyer", "최구매", buyTeam, p, Role.PURCHASING);
        Position manager = f.position("과장", 3);
        Position deputy = f.position("차장", 4);
        Position director = f.position("부장", 5);
        buyLead = f.member("buylead", "강구매", buyTeam, deputy, Role.PURCHASING);
        devLead = f.member("devlead", "이팀장", devTeam, manager, Role.TEAM_LEADER);
        salesLead = f.member("saleslead", "윤팀장", salesTeam, deputy, Role.TEAM_LEADER);
        bizHead = f.member("bizhead", "박본부", division, director, Role.DEPT_HEAD);

        laptop = persist(new Item("IT-0001", "노트북", "IT", "EA", new BigDecimal("1650000")));
        monitor = persist(new Item("IT-0002", "모니터", "IT", "EA", new BigDecimal("320000")));
        paper = persist(new Item("OF-0001", "복사용지", "OFFICE", "BOX", new BigDecimal("27000")));

        activeVendor = new Vendor("1018100001", "(주)오피스몰", "김오피");
        activeVendor.changeStatus(VendorStatus.ACTIVE, LocalDateTime.now());
        persist(activeVendor);
        closedVendor = new Vendor("1018100006", "옛날상사", "강옛날");
        closedVendor.changeStatus(VendorStatus.CLOSED, LocalDateTime.now());
        persist(closedVendor);
        em.flush();
    }

    /** 노트북 2 + 모니터 2 + 복사용지 1 짜리 임시저장 요청서 */
    public PurchaseRequest draft(Member requester, String title) {
        PurchaseRequest r = PurchaseRequest.draft(requester, title, "WORK", "사유", activeVendor);
        r.replaceItems(List.of(
                new PurchaseRequest.Line(laptop, 2, laptop.getStandardPrice()),
                new PurchaseRequest.Line(monitor, 2, monitor.getStandardPrice()),
                new PurchaseRequest.Line(paper, 1, paper.getStandardPrice())));
        em.persist(r);
        r.assignRequestNo(LocalDateTime.now());
        return r;
    }

    /** 노트북 n대짜리 요청서. 4대면 660만 원 → 구매팀장 결재까지 (BR-01) */
    public PurchaseRequest draftLaptops(Member requester, String title, int count) {
        PurchaseRequest r = PurchaseRequest.draft(requester, title, "WORK", "사유", activeVendor);
        r.replaceItems(List.of(new PurchaseRequest.Line(laptop, count, laptop.getStandardPrice())));
        em.persist(r);
        r.assignRequestNo(LocalDateTime.now());
        return r;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
