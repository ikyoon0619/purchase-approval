package com.example.purchase.org.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.web.DepartmentForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 클래스 전체를 readOnly 트랜잭션으로 두고, 데이터를 바꾸는 메서드만 @Transactional을 다시 붙인다.
 * readOnly면 JPA가 변경 감지(스냅샷 비교)를 하지 않아 조회가 조금 가벼워진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<Department> search(String keyword, Boolean used) {
        return departmentRepository.search(keyword, used);
    }

    /** 상위 부서로 고를 수 있는 본부 목록 */
    public List<Department> divisions() {
        return departmentRepository.search(null, true).stream()
                .filter(Department::isDivision)
                .toList();
    }

    public List<Department> activeDepartments() {
        return departmentRepository.search(null, true);
    }

    public Department get(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Transactional
    public Long create(DepartmentForm form) {
        if (departmentRepository.findByCode(form.getCode()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_DEPARTMENT_CODE);
        }
        Department parent = parentOf(form);
        Department department = parent == null
                ? Department.division(form.getCode(), form.getName())
                : Department.team(form.getCode(), form.getName(), parent);
        return departmentRepository.save(department).getId();
    }

    /**
     * save()를 부르지 않아도 된다. 트랜잭션 안에서 읽은 엔티티의 값을 바꾸면
     * 커밋할 때 JPA가 바뀐 것을 찾아 UPDATE를 날린다 (변경 감지, dirty checking).
     */
    @Transactional
    public void update(Long id, DepartmentForm form) {
        Department department = get(id);
        try {
            department.update(form.getName(), parentOf(form), form.isUsed());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARENT_DEPARTMENT);
        }
    }

    private Department parentOf(DepartmentForm form) {
        return form.getParentId() == null ? null : get(form.getParentId());
    }
}
