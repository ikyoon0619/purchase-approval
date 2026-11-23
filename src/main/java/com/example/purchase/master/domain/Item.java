package com.example.purchase.master.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 품목. 분류와 단위는 공통코드 값(문자열)을 그대로 저장한다.
 * 공통코드를 @ManyToOne으로 묶지 않은 이유: 코드는 "값"이라 조회만 하면 되고, 묶으면 품목 하나 읽을 때마다 join이 늘어난다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 공통코드 ITEM_CATEGORY */
    @Column(nullable = false, length = 30)
    private String categoryCode;

    /** 공통코드 UNIT */
    @Column(nullable = false, length = 30)
    private String unitCode;

    /** 기준 단가. 돈은 double이 아니라 BigDecimal (0.1 + 0.2 != 0.3 문제) */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal standardPrice;

    @Column(name = "use_yn", nullable = false)
    private boolean used = true;

    public Item(String code, String name, String categoryCode, String unitCode, BigDecimal standardPrice) {
        this.code = code;
        this.name = name;
        this.categoryCode = categoryCode;
        this.unitCode = unitCode;
        this.standardPrice = standardPrice;
    }

    public void update(String name, String categoryCode, String unitCode, BigDecimal standardPrice, boolean used) {
        this.name = name;
        this.categoryCode = categoryCode;
        this.unitCode = unitCode;
        this.standardPrice = standardPrice;
        this.used = used;
    }
}
