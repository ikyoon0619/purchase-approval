package com.example.purchase.org.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직급. rankOrder가 클수록 높다 (사원 1 … 부장 5). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(nullable = false)
    private int rankOrder;

    public Position(String name, int rankOrder) {
        this.name = name;
        this.rankOrder = rankOrder;
    }

    public void update(String name, int rankOrder) {
        this.name = name;
        this.rankOrder = rankOrder;
    }
}
