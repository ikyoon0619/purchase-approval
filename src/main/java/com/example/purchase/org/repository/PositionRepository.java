package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findAllByOrderByRankOrderAsc();

    boolean existsByName(String name);
}
