package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 간단한 조회는 Spring Data JPA 메서드 이름 규칙으로,
 * 조건이 바뀌는 검색은 DepartmentRepositoryCustom(QueryDSL)으로 만든다.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long>, DepartmentRepositoryCustom {

    Optional<Department> findByCode(String code);
}
