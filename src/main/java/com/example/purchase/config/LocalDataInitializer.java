package com.example.purchase.config;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로필에서 앱이 뜰 때 샘플 조직도를 넣는다.
 * 이미 데이터가 있으면 아무것도 하지 않으므로 여러 번 재시작해도 중복으로 쌓이지 않는다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (departmentRepository.count() > 0) {
            return;
        }
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
        log.info("샘플 조직도를 넣었습니다: 부서 {}개, 직급 {}개", departmentRepository.count(), positionRepository.count());
    }
}
