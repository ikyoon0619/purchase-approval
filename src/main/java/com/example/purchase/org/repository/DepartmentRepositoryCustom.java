package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Department;

import java.util.List;

public interface DepartmentRepositoryCustom {

    /** 이름 일부와 사용 여부로 검색. 조건이 null이면 그 조건은 빼고 검색한다 */
    List<Department> search(String keyword, Boolean used);
}
