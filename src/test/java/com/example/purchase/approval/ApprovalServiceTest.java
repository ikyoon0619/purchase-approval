package com.example.purchase.approval;

import com.example.purchase.approval.repository.InboxTab;
import com.example.purchase.approval.service.ApprovalLineResolver;
import com.example.purchase.approval.service.ApprovalService;
import com.example.purchase.approval.service.InboxRow;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.LineStatus;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.RequestStatus;
import com.example.purchase.request.service.RequestDetail;
import com.example.purchase.request.service.RequestService;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** 결재선 자동 생성(BR-01)과 상신·회수·승인·반려 흐름 (BR-02~04) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApprovalServiceTest {

    @Autowired ApprovalService approvalService;
    @Autowired ApprovalLineResolver resolver;
    @Autowired RequestService requestService;
    @Autowired EntityManager em;

    RequestFixture f;
    LoginUser dev, devLead, bizHead, buyLead, salesLead;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        dev = new LoginUser(f.dev);
        devLead = new LoginUser(f.devLead);
        bizHead = new LoginUser(f.bizHead);
        buyLead = new LoginUser(f.buyLead);
        salesLead = new LoginUser(f.salesLead);
    }

    // ---------------- BR-01 결재선 ----------------

    @Test
    @DisplayName("BR-01: 500만 원 미만이면 팀장 → 본부장")
    void lineUnderThreshold() {
        PurchaseRequest r = f.draft(f.dev, "노트북 구매"); // 396만 7천 원

        assertThat(resolver.resolve(r)).extracting(a -> a.stepName(), a -> a.member().getName())
                .containsExactly(tuple("팀장", "이팀장"), tuple("본부장", "박본부"));
    }

    @Test
    @DisplayName("BR-01: 500만 원 이상이면 구매팀장까지. 구매팀 중 직급이 가장 높은 사람(차장)이 들어간다")
    void lineOverThreshold() {
        PurchaseRequest r = f.draftLaptops(f.dev, "노트북 4대", 4); // 660만 원

        assertThat(resolver.resolve(r)).extracting(a -> a.member().getName())
                .containsExactly("이팀장", "박본부", "강구매");
    }

    @Test
    @DisplayName("BR-01: 팀장이 직접 올리면 팀장 단계는 건너뛴다")
    void skipSelf() {
        PurchaseRequest r = f.draft(f.devLead, "팀장 요청");

        assertThat(resolver.resolve(r)).extracting(a -> a.stepName()).containsExactly("본부장");
    }

    @Test
    @DisplayName("BR-01: 팀장이 없는 팀(구매팀)은 상신할 수 없고, 누가 없는지 알려 준다")
    void approverNotFound() {
        PurchaseRequest r = f.draft(f.buyer, "구매팀 요청");

        assertThatThrownBy(() -> resolver.resolve(r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("구매팀 팀장")
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVER_NOT_FOUND);
    }

    @Test
    @DisplayName("상신 전 미리보기: 결재선을 보여 주고, 만들 수 없으면 이유를 보여 준다")
    void preview() {
        Long ok = f.draft(f.dev, "노트북").getId();
        Long fail = f.draft(f.buyer, "구매팀 요청").getId();

        assertThat(approvalService.preview(ok).steps()).extracting(ApprovalService.Preview.Step::positionName)
                .containsExactly("과장", "부장");
        assertThat(approvalService.preview(fail).error()).contains("구매팀 팀장");
    }

    // ---------------- 상신 → 승인 → 승인 ----------------

    @Test
    @DisplayName("상신하면 1단계만 결재 차례, 끝까지 승인하면 문서가 승인으로 끝난다")
    void submitAndApproveAll() {
        Long id = f.draftLaptops(f.dev, "노트북 4대", 4).getId();

        approvalService.submit(id, dev);
        PurchaseRequest r = flushAndFind(id);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.IN_APPROVAL);
        assertThat(r.currentLines()).extracting(ApprovalLine::getStatus)
                .containsExactly(LineStatus.PENDING, LineStatus.WAITING, LineStatus.WAITING);
        assertThat(r.isEditable()).isFalse();

        approvalService.approve(id, devLead, "확인했습니다.");
        approvalService.approve(id, bizHead, null);
        assertThat(flushAndFind(id).getStatus()).isEqualTo(RequestStatus.IN_APPROVAL); // 아직 구매팀장 남음

        approvalService.approve(id, buyLead, "발주 진행하겠습니다.");
        r = flushAndFind(id);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(r.getCompletedAt()).isNotNull();
        assertThat(r.currentLines()).extracting(ApprovalLine::getStatus, ApprovalLine::getComment).containsExactly(
                tuple(LineStatus.APPROVED, "확인했습니다."),
                tuple(LineStatus.APPROVED, null),
                tuple(LineStatus.APPROVED, "발주 진행하겠습니다."));
    }

    @Test
    @DisplayName("BR-02: 내 차례가 아니면 처리할 수 없다 (본부장이 팀장보다 먼저 승인)")
    void notYourTurn() {
        Long id = submitted();

        assertThatThrownBy(() -> approvalService.approve(id, bizHead, null))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_YOUR_TURN);
        assertThatThrownBy(() -> approvalService.approve(id, salesLead, null))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_YOUR_TURN);
    }

    @Test
    @DisplayName("이미 끝난 문서를 다시 승인하면 '다른 사용자가 먼저 처리했습니다'")
    void alreadyProcessed() {
        Long id = submitted();
        approvalService.reject(id, devLead, "예산 초과");

        assertThatThrownBy(() -> approvalService.approve(id, devLead, null))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("팀장이 승인한 뒤 같은 문서를 또 승인하면(두 탭) '차례 아님'이 아니라 '이미 처리됨'")
    void approveTwiceSequentially() {
        Long id = submitted();
        approvalService.approve(id, devLead, null);

        assertThatThrownBy(() -> approvalService.approve(id, devLead, null))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED);
    }

    // ---------------- 반려, 회수 ----------------

    @Test
    @DisplayName("BR-03: 반려는 의견 필수, 반려하면 남은 단계는 취소")
    void reject() {
        Long id = submitted();

        assertThatThrownBy(() -> approvalService.reject(id, devLead, "  "))
                .extracting("errorCode").isEqualTo(ErrorCode.REJECT_COMMENT_REQUIRED);

        approvalService.reject(id, devLead, "다음 분기 예산으로 다시 올려 주세요.");
        PurchaseRequest r = flushAndFind(id);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(r.currentLines()).extracting(ApprovalLine::getStatus)
                .containsExactly(LineStatus.REJECTED, LineStatus.CANCELED);
    }

    @Test
    @DisplayName("BR-04: 첫 결재 전에는 회수 → 임시저장으로 돌아가 다시 고치고 상신할 수 있다 (회차 2)")
    void withdrawAndResubmit() {
        Long id = submitted();

        approvalService.withdraw(id, dev);
        PurchaseRequest r = flushAndFind(id);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.DRAFT);
        assertThat(r.getApprovalLines()).extracting(ApprovalLine::getStatus)
                .containsOnly(LineStatus.CANCELED);

        approvalService.submit(id, dev);
        r = flushAndFind(id);
        assertThat(r.getSubmitRound()).isEqualTo(2);
        assertThat(r.getApprovalLines()).hasSize(4);    // 1회차 2줄(취소) + 2회차 2줄
        assertThat(r.currentLines()).extracting(ApprovalLine::getStatus)
                .containsExactly(LineStatus.PENDING, LineStatus.WAITING);
    }

    @Test
    @DisplayName("BR-04: 팀장이 승인한 뒤에는 회수할 수 없다. 남의 문서도 회수할 수 없다")
    void cannotWithdraw() {
        Long id = submitted();
        assertThatThrownBy(() -> approvalService.withdraw(id, devLead))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_WITHDRAW);

        approvalService.approve(id, devLead, null);
        assertThatThrownBy(() -> approvalService.withdraw(id, dev))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_WITHDRAW);
    }

    // ---------------- 화면에 넘기는 값 ----------------

    @Test
    @DisplayName("상세: 결재선에 있는 사람은 볼 수 있고 자기 차례에만 승인 버튼이 보인다. 다른 팀장은 못 본다")
    void detailForApprover() {
        Long id = submitted();
        flushAndFind(id);

        RequestDetail forLead = requestService.getDetail(id, devLead);
        assertThat(forLead.canApprove()).isTrue();
        assertThat(forLead.steps()).extracting(RequestDetail.Step::approverName).containsExactly("이팀장", "박본부");

        RequestDetail forHead = requestService.getDetail(id, bizHead);
        assertThat(forHead.canApprove()).isFalse();

        RequestDetail forOwner = requestService.getDetail(id, dev);
        assertThat(forOwner.canWithdraw()).isTrue();
        assertThat(forOwner.editable()).isFalse();

        assertThatThrownBy(() -> requestService.getDetail(id, salesLead))
                .extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
    }

    @Test
    @DisplayName("결재함: 대기 / 내가 처리한 문서 / 진행 중 문서 탭이 서로 다른 문서를 보여 준다")
    void inbox() {
        Long first = submitted();
        Long second = submitted();
        approvalService.approve(first, devLead, null);  // first: 본부장 차례로 넘어감
        em.flush();
        em.clear();

        assertThat(titles(devLead, InboxTab.PENDING)).containsExactly(second);
        assertThat(titles(devLead, InboxTab.DONE)).containsExactly(first);
        assertThat(titles(devLead, InboxTab.PROGRESS)).containsExactly(first);
        assertThat(titles(bizHead, InboxTab.PENDING)).containsExactly(first);
        assertThat(titles(bizHead, InboxTab.PROGRESS)).containsExactly(second); // 내 차례가 아직 안 온 문서
        assertThat(approvalService.pendingCount(f.devLead.getId())).isEqualTo(1);
    }

    private List<Long> titles(LoginUser user, InboxTab tab) {
        return approvalService.inbox(user, tab, PageRequest.of(0, 10)).map(InboxRow::requestId).getContent();
    }

    private Long submitted() {
        Long id = f.draft(f.dev, "노트북 구매").getId();
        approvalService.submit(id, dev);
        return id;
    }

    private PurchaseRequest flushAndFind(Long id) {
        em.flush();
        em.clear();
        return em.find(PurchaseRequest.class, id);
    }
}
