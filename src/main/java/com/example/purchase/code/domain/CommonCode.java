package com.example.purchase.code.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공통코드 한 건. 예) ITEM_CATEGORY 그룹의 "IT" = "IT기기" */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_common_code", columnNames = {"group_code", "code"}))
public class CommonCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_code", nullable = false)
    private CodeGroup group;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(name = "use_yn", nullable = false)
    private boolean used = true;

    /** 그룹을 통해서만 만든다 ({@link CodeGroup#addCode}) */
    CommonCode(CodeGroup group, String code, String name, int sortOrder) {
        this.group = group;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    /** 코드 값은 다른 테이블(품목 등)에 저장되는 키라서 바꾸지 않는다 */
    public void update(String name, int sortOrder, boolean used) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.used = used;
    }
}
