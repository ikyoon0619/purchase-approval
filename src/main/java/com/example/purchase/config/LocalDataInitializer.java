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
