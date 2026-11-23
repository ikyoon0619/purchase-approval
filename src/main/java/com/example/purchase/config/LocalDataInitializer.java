package com.example.purchase.config;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.MemberRepository;
import com.example.purchase.org.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.purchase.master.domain.Item;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.repository.ItemRepository;
import com.example.purchase.master.repository.VendorRepository;
import com.example.purchase.approval.repository.ApprovalLineRepository;
import com.example.purchase.approval.service.ApprovalLineResolver;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;

import java.util.List;
import java.util.Map;

/**
 * local 프로필에서 샘플 조직도와 샘플 계정을 넣는다. 이미 있으면 건너뛴다.
 * 샘플 계정 비밀번호는 모두 {@value #SAMPLE_PASSWORD} (README에도 적어 둠, 로컬 전용)
 */
@Slf4j
@Component
@Profile("local")
@Order(2)
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    static final String SAMPLE_PASSWORD = "pass1234";

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CodeInitializer codeInitializer;
    private final ItemRepository itemRepository;
    private final VendorRepository vendorRepository;
    private final PurchaseRequestRepository requestRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalLineResolver approvalLineResolver;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (departmentRepository.count() == 0) {
            Department support = departmentRepository.save(Department.division("D100", "경영지원본부"));
            Department business = departmentRepository.save(Department.division("D200", "사업본부"));
            departmentRepository.save(Department.team("D110", "구매팀", support));
            departmentRepository.save(Department.team("D120", "인사팀", support));
            departmentRepository.save(Department.team("D210", "개발팀", business));
            departmentRepository.save(Department.team("D220", "영업팀", business));

            String[] names = {"사원", "대리", "과장", "차장", "부장"};
            for (int i = 0; i < names.length; i++) {
                positionRepository.save(new Position(names[i], i + 1));
            }
            log.info("샘플 조직도를 넣었습니다.");
        }
        if (memberRepository.count() == 0) {
            insertSampleMembers();
        }
        // 4단계: 페이징을 눈으로 확인할 수 있게 직원 30명과 코드 그룹을 더 넣는다
        if (!memberRepository.existsByLoginId("emp01")) {
            insertPagingSampleMembers();
        }
        insertSampleCodeGroups();
        // 5단계: 품목·거래처, dev1의 임시저장 요청서
        if (itemRepository.count() == 0) {
            insertMasterData();
        }
        if (requestRepository.count() == 0) {
            insertSampleRequests();
        }
        // 6단계: 결재함에 문서가 보이도록 상신·승인·반려된 샘플
        if (approvalLineRepository.count() == 0) {
            insertApprovalSamples();
        }
    }

    /**
     * dev2: 결재 대기(이팀장 차례), 660만 원짜리(구매팀장 강구매 차례)
     * sales1: 본부장 박본부 차례, 윤팀장이 반려한 문서
     */
    private void insertApprovalSamples() {
        Member dev2 = memberRepository.findByLoginIdWithDepartment("dev2").orElse(null);
        Member sales1 = memberRepository.findByLoginIdWithDepartment("sales1").orElse(null);
        Member devlead = memberRepository.findByLoginIdWithDepartment("devlead").orElse(null);
        Member saleslead = memberRepository.findByLoginIdWithDepartment("saleslead").orElse(null);
        Member bizhead = memberRepository.findByLoginIdWithDepartment("bizhead").orElse(null);
        if (dev2 == null || sales1 == null || devlead == null || saleslead == null || bizhead == null) {
            return;
        }
        Map<String, Item> items = new java.util.HashMap<>();
        itemRepository.findAll().forEach(i -> items.put(i.getCode(), i));
        Vendor vendor = vendorRepository.findAll().stream().filter(Vendor::isSelectable).findFirst().orElseThrow();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        PurchaseRequest a = sample(dev2, "회의실 모니터 교체", vendor, items.get("IT-0002"), 2, now.minusDays(1));
        PurchaseRequest b = sample(dev2, "개발 서버 점검용 노트북 4대", vendor, items.get("IT-0001"), 4, now.minusDays(2));
        b.approve(devlead.getId(), "필요성 확인했습니다.", now.minusDays(2).plusHours(3));
        b.approve(bizhead.getId(), null, now.minusDays(1));
        PurchaseRequest c = sample(sales1, "영업팀 복사용지와 토너", vendor, items.get("CS-0001"), 3, now.minusHours(5));
        c.approve(saleslead.getId(), "승인합니다.", now.minusHours(4));
        PurchaseRequest d = sample(sales1, "영업팀 의자 교체 10개", vendor, items.get("FN-0001"), 10, now.minusDays(3));
        d.reject(saleslead.getId(), "이번 분기 예산을 넘습니다. 5개로 줄여 다시 올려 주세요.", now.minusDays(2));
        log.info("결재 샘플 문서 4건을 넣었습니다.");
    }

    private PurchaseRequest sample(Member requester, String title, Vendor vendor, Item item, int qty, java.time.LocalDateTime submittedAt) {
        PurchaseRequest r = PurchaseRequest.draft(requester, title, "WORK", "샘플 요청서입니다.", vendor);
        r.replaceItems(List.of(new PurchaseRequest.Line(item, qty, item.getStandardPrice())));
        requestRepository.save(r);
        r.assignRequestNo(submittedAt);
        r.submit(approvalLineResolver.resolve(r), submittedAt);
        return r;
    }

    private void insertMasterData() {
        Object[][] items = {
                {"IT-0001", "노트북 14인치", "IT", "EA", 1_650_000},
                {"IT-0002", "27인치 모니터", "IT", "EA", 320_000},
                {"IT-0003", "무선 키보드·마우스 세트", "IT", "SET", 59_000},
                {"IT-0004", "USB-C 도킹 스테이션", "IT", "EA", 189_000},
                {"OF-0001", "A4 복사용지 (2,500매)", "OFFICE", "BOX", 27_000},
                {"OF-0002", "볼펜 12자루", "OFFICE", "SET", 6_500},
                {"OF-0003", "화이트보드 마커 4색", "OFFICE", "SET", 7_800},
                {"CS-0001", "토너 카트리지 (검정)", "CONSUMABLE", "EA", 98_000},
                {"CS-0002", "생수 2L × 6", "CONSUMABLE", "BOX", 5_900},
                {"FN-0001", "사무용 의자", "FURNITURE", "EA", 280_000},
                {"FN-0002", "높이조절 책상", "FURNITURE", "EA", 450_000},
                {"SW-0001", "오피스 프로그램 1년", "SOFTWARE", "LICENSE", 119_000},
        };
        for (Object[] r : items) {
            itemRepository.save(new Item((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    java.math.BigDecimal.valueOf((Integer) r[4])));
        }
        // 사업자번호는 예시 값 (실제 사업자 아님)
        Object[][] vendors = {
                {"1018100001", "(주)오피스몰", "김오피", VendorStatus.ACTIVE},
                {"1018100002", "테크디바이스(주)", "이테크", VendorStatus.ACTIVE},
                {"1018100003", "한빛가구", "박가구", VendorStatus.ACTIVE},
                {"1018100004", "소프트라이선스코리아", "최소프", VendorStatus.UNKNOWN},
                {"1018100005", "대명문구 (휴업)", "정문구", VendorStatus.SUSPENDED},
                {"1018100006", "옛날상사 (폐업)", "강옛날", VendorStatus.CLOSED},
        };
        for (Object[] r : vendors) {
            Vendor v = new Vendor((String) r[0], (String) r[1], (String) r[2]);
            v.changeStatus((VendorStatus) r[3], r[3] == VendorStatus.UNKNOWN ? null : java.time.LocalDateTime.now());
            vendorRepository.save(v);
        }
        log.info("샘플 품목 {}개, 거래처 {}곳을 넣었습니다.", items.length, vendors.length);
    }

    private void insertSampleRequests() {
        Member dev1 = memberRepository.findByLoginIdWithDepartment("dev1").orElse(null);
        if (dev1 == null) {
            return;
        }
        List<Item> items = itemRepository.findAll();
        List<Vendor> vendors = vendorRepository.findAll();
        String[] titles = {"신규 입사자 노트북·모니터 구매", "개발팀 사무용품 보충", "회의실 화이트보드 마커와 토너"};
        int[][] lines = {{0, 2, 1, 2}, {4, 3, 5, 2}, {6, 4, 7, 1}}; // {품목 index, 수량, 품목 index, 수량}
        for (int n = 0; n < titles.length; n++) {
            PurchaseRequest r = PurchaseRequest.draft(dev1, titles[n], "WORK", "샘플 요청서입니다.", vendors.get(n == 0 ? 1 : 0));
            r.replaceItems(List.of(
                    new PurchaseRequest.Line(items.get(lines[n][0]), lines[n][1], items.get(lines[n][0]).getStandardPrice()),
                    new PurchaseRequest.Line(items.get(lines[n][2]), lines[n][3], items.get(lines[n][2]).getStandardPrice())));
            requestRepository.save(r);
            r.assignRequestNo(java.time.LocalDateTime.now());
        }
        log.info("dev1의 샘플 구매요청서 {}건을 넣었습니다.", titles.length);
    }

    private void insertPagingSampleMembers() {
        Map<String, Position> pos = new java.util.HashMap<>();
        positionRepository.findAll().forEach(p -> pos.put(p.getName(), p));
        String[] teams = {"D110", "D120", "D210", "D220"};
        String[] lastNames = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};
        String[] firstNames = {"민준", "서연", "도윤", "하은", "시우"};
        String pw = passwordEncoder.encode(SAMPLE_PASSWORD);
        for (int i = 1; i <= 30; i++) {
            String loginId = String.format("emp%02d", i);
            String name = lastNames[i % lastNames.length] + firstNames[(i / 10 + i) % firstNames.length]; // 30명 이름이 겹치지 않게
            Department dept = departmentRepository.findByCode(teams[i % teams.length]).orElseThrow();
            memberRepository.save(new Member(loginId, pw, name, loginId + "@example.com",
                    dept, pos.get(i % 3 == 0 ? "대리" : "사원"), Role.EMPLOYEE));
        }
        log.info("페이징 확인용 직원 30명을 넣었습니다.");
    }

    private void insertSampleCodeGroups() {
        codeInitializer.add("BUDGET_TYPE", "예산 구분", new String[][]{{"OPEX", "운영비"}, {"CAPEX", "투자비"}});
        codeInitializer.add("PAYMENT_TERMS", "결제 조건", new String[][]{{"NET30", "월말 30일"}, {"NET60", "월말 60일"}, {"PREPAY", "선결제"}});
        codeInitializer.add("DELIVERY_PLACE", "납품 장소", new String[][]{{"HQ", "본사"}, {"WH1", "1창고"}, {"LAB", "연구소"}});
        codeInitializer.add("REJECT_REASON", "반려 사유", new String[][]{{"BUDGET", "예산 초과"}, {"DUP", "중복 요청"}, {"SPEC", "사양 부적합"}});
        codeInitializer.add("CURRENCY", "통화", new String[][]{{"KRW", "원"}, {"USD", "달러"}});
        codeInitializer.add("VENDOR_TYPE", "거래처 유형", new String[][]{{"MAKER", "제조사"}, {"DIST", "유통사"}, {"SERVICE", "서비스"}});
        codeInitializer.add("STORAGE", "보관 위치", new String[][]{{"A1", "A동 1층"}, {"A2", "A동 2층"}, {"B1", "B동 1층"}});
        codeInitializer.add("ASSET_TYPE", "자산 구분", new String[][]{{"ASSET", "자산"}, {"EXPENSE", "비용"}});
        codeInitializer.add("URGENCY", "긴급도", new String[][]{{"NORMAL", "보통"}, {"HIGH", "긴급"}});
    }

    private void insertSampleMembers() {
        Map<String, Position> pos = new java.util.HashMap<>();
        positionRepository.findAll().forEach(p -> pos.put(p.getName(), p));
        String pw = passwordEncoder.encode(SAMPLE_PASSWORD);

        // 아이디, 이름, 부서 코드, 직급, 역할
        Object[][] rows = {
                {"admin", "관리자", "D120", "과장", Role.ADMIN},
                {"dev1", "김개발", "D210", "사원", Role.EMPLOYEE},
                {"dev2", "오코드", "D210", "대리", Role.EMPLOYEE},
                {"devlead", "이팀장", "D210", "과장", Role.TEAM_LEADER},
                {"sales1", "정영업", "D220", "사원", Role.EMPLOYEE},
                {"saleslead", "윤팀장", "D220", "차장", Role.TEAM_LEADER},
                {"bizhead", "박본부", "D200", "부장", Role.DEPT_HEAD},
                {"buyer", "최구매", "D110", "대리", Role.PURCHASING},
                {"buylead", "강구매", "D110", "차장", Role.PURCHASING},
                {"supporthead", "한지원", "D100", "부장", Role.DEPT_HEAD},
        };
        for (Object[] r : rows) {
            Department dept = departmentRepository.findByCode((String) r[2]).orElseThrow();
            memberRepository.save(new Member((String) r[0], pw, (String) r[1], r[0] + "@example.com",
                    dept, pos.get((String) r[3]), (Role) r[4]));
        }
        log.info("샘플 계정 {}개를 넣었습니다. 비밀번호: {}", rows.length, SAMPLE_PASSWORD);
    }
}
