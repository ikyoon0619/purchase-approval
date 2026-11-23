package com.example.purchase.request;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.common.file.FileStorage;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.request.repository.RequestSearchCond;
import com.example.purchase.request.service.RequestDetail;
import com.example.purchase.request.service.RequestRow;
import com.example.purchase.request.service.RequestService;
import com.example.purchase.request.web.RequestForm;
import com.example.purchase.request.web.RequestItemForm;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequestServiceTest {

    @Autowired RequestService requestService;
    @Autowired PurchaseRequestRepository requestRepository;
    @Autowired FileStorage fileStorage;
    @Autowired EntityManager em;

    RequestFixture f;
    LoginUser dev;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        dev = new LoginUser(f.dev);
    }

    @Test
    @DisplayName("작성: 화면에서 합계를 안 받아도 서버가 계산하고, 문서번호와 첨부가 저장된다")
    void create() {
        RequestForm form = form(f.activeVendor.getId(),
                new RequestItemForm(f.laptop.getId(), 2, new BigDecimal("1600000")),
                new RequestItemForm(f.paper.getId(), 1, new BigDecimal("27000")));

        Long id = requestService.create(form, List.of(pdf("견적서.pdf")), dev);
        em.flush();
        em.clear();

        RequestDetail d = requestService.getDetail(id, dev);
        assertThat(d.requestNo()).startsWith("PR-").endsWith(String.format("-%04d", id));
        assertThat(d.totalAmount()).isEqualByComparingTo("3227000");
        assertThat(d.departmentName()).isEqualTo("개발팀");
        assertThat(d.attachments()).extracting(RequestDetail.File::originalName).containsExactly("견적서.pdf");
        PurchaseRequest saved = requestRepository.findById(id).orElseThrow();
        assertThat(fileStorage.exists(saved.getAttachments().get(0).getStoredName())).isTrue();
    }

    @Test
    @DisplayName("폐업 거래처는 고를 수 없다 (BR-09)")
    void closedVendor() {
        RequestForm form = form(f.closedVendor.getId(), new RequestItemForm(f.paper.getId(), 1, BigDecimal.TEN));

        assertThatThrownBy(() -> requestService.create(form, List.of(), dev))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VENDOR_NOT_SELECTABLE);
    }

    @Test
    @DisplayName("첨부가 5개를 넘으면 파일을 쓰기 전에 거절한다")
    void tooManyFiles() {
        RequestForm form = form(f.activeVendor.getId(), new RequestItemForm(f.paper.getId(), 1, BigDecimal.TEN));
        List<MultipartFile> six = List.of(pdf("1.pdf"), pdf("2.pdf"), pdf("3.pdf"), pdf("4.pdf"), pdf("5.pdf"), pdf("6.pdf"));

        assertThatThrownBy(() -> requestService.create(form, six, dev))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_ATTACHMENTS);
    }

    @Test
    @DisplayName("보기 권한: 기안자와 구매팀은 되고, 다른 직원은 안 된다. 수정은 기안자만")
    void accessControl() {
        Long id = f.draft(f.dev, "노트북").getId();

        assertThat(requestService.getDetail(id, new LoginUser(f.buyer)).title()).isEqualTo("노트북");
        assertThatThrownBy(() -> requestService.getDetail(id, new LoginUser(f.sales)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
        assertThatThrownBy(() -> requestService.getForEdit(id, new LoginUser(f.buyer)))
                .extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
    }

    @Test
    @DisplayName("수정: 품목 줄을 바꾸고 첨부를 지우면 줄과 첨부 행이 DB에서도 지워진다 (orphanRemoval)")
    void update() {
        Long id = requestService.create(form(f.activeVendor.getId(),
                new RequestItemForm(f.laptop.getId(), 1, f.laptop.getStandardPrice())), List.of(pdf("a.pdf")), dev);
        em.flush();
        em.clear();
        Long attachmentId = requestService.getDetail(id, dev).attachments().get(0).id();

        RequestForm edit = form(f.activeVendor.getId(), new RequestItemForm(f.paper.getId(), 4, new BigDecimal("25000")));
        edit.setDeleteAttachmentIds(List.of(attachmentId));
        requestService.update(id, edit, List.of(), dev);
        em.flush();
        em.clear();

        RequestDetail d = requestService.getDetail(id, dev);
        assertThat(d.items()).extracting(RequestDetail.Line::itemName).containsExactly("복사용지");
        assertThat(d.totalAmount()).isEqualByComparingTo("100000");
        assertThat(d.attachments()).isEmpty();
        Long itemRows = em.createQuery("select count(i) from PurchaseRequestItem i where i.request.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        assertThat(itemRows).isEqualTo(1);
    }

    @Test
    @DisplayName("내 요청 목록: 남의 요청은 안 보이고, 제목·기간으로 거르고, 품목 요약이 나온다")
    void searchMine() {
        f.draft(f.dev, "노트북 구매");
        f.draft(f.dev, "사무용품 보충");
        f.draft(f.sales, "영업팀 요청");
        em.flush();
        em.clear();

        Page<RequestRow> all = requestService.searchMine(dev, RequestSearchCond.empty(), PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(RequestRow::title).containsExactly("사무용품 보충", "노트북 구매"); // 최신순
        assertThat(all.getContent().get(0).itemSummary()).isEqualTo("노트북 외 2건");

        RequestSearchCond byTitle = new RequestSearchCond("노트북", null, LocalDate.now(), LocalDate.now());
        assertThat(requestService.searchMine(dev, byTitle, PageRequest.of(0, 10)).getContent()).hasSize(1);

        RequestSearchCond yesterday = new RequestSearchCond(null, null, null, LocalDate.now().minusDays(1));
        assertThat(requestService.searchMine(dev, yesterday, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("삭제: 임시저장만, 기안자만")
    void delete() {
        Long id = f.draft(f.dev, "지울 요청").getId();

        assertThatThrownBy(() -> requestService.delete(id, new LoginUser(f.sales)))
                .extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
        requestService.delete(id, dev);
        em.flush();
        assertThat(requestRepository.findById(id)).isEmpty();
    }

    private RequestForm form(Long vendorId, RequestItemForm... items) {
        RequestForm form = new RequestForm();
        form.setTitle("테스트 요청");
        form.setPurposeCode("WORK");
        form.setVendorId(vendorId);
        form.setItems(List.of(items));
        return form;
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
    }
}
