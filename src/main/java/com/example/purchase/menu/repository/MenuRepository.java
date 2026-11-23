package com.example.purchase.menu.repository;

import com.example.purchase.menu.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** 메뉴와 역할 목록을 한 번에. 컬렉션을 fetch join하면 행이 늘어나므로 distinct로 중복 메뉴를 없앤다 */
    @Query("select distinct m from Menu m left join fetch m.roles order by m.sortOrder")
    List<Menu> findAllWithRoles();

    Optional<Menu> findByUrl(String url);
}
