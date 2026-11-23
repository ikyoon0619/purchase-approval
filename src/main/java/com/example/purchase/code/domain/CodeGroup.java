package com.example.purchase.code.domain;

import com.example.purchase.common.entity.BaseEntity;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.ArrayList;
import java.util.List;

/**
 * 공통코드 그룹. 예) ITEM_CATEGORY(품목 분류), UNIT(단위)
 * 화면의 select 박스 값처럼 자주 바뀌지 않는 목록을 코드 수정 없이 관리자가 바꿀 수 있게 한다.
 *
 * <p>그룹 코드 자체가 PK(문자열)라서 {@link Persistable}을 구현한다.
 * 안 하면 save()가 "id가 이미 있으니 기존 데이터"로 보고 merge → select를 한 번 더 날린다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeGroup extends BaseEntity implements Persistable<String> {

    @Id
    @Column(length = 30)
    private String groupCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "use_yn", nullable = false)
    private boolean used = true;

    /**
     * 그룹에 속한 코드. 그룹이 코드의 생명주기를 관리한다 (그룹에서 빼면 코드도 삭제: orphanRemoval).
     * 컬렉션은 기본이 LAZY. 목록 화면에서 여러 그룹의 코드를 읽을 때는 default_batch_fetch_size로 묶어 읽는다.
     */
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, code asc")
    private List<CommonCode> codes = new ArrayList<>();

    public CodeGroup(String groupCode, String name) {
        this.groupCode = groupCode;
        this.name = name;
    }

    public void update(String name, boolean used) {
        this.name = name;
        this.used = used;
    }

    /** 코드 추가. 같은 그룹 안에서 코드 값은 겹치면 안 된다 */
    public CommonCode addCode(String code, String name, int sortOrder) {
        boolean exists = codes.stream().anyMatch(c -> c.getCode().equals(code));
        if (exists) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        CommonCode commonCode = new CommonCode(this, code, name, sortOrder);
        codes.add(commonCode); // 양방향 연관관계: 컬렉션 쪽에도 넣어 줘야 같은 트랜잭션 안에서 바로 보인다
        return commonCode;
    }

    public CommonCode findCode(Long codeId) {
        return codes.stream()
                .filter(c -> c.getId().equals(codeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_NOT_FOUND));
    }

    // ---- Persistable ----

    @Override
    public String getId() {
        return groupCode;
    }

    /** 생성 시각이 아직 없으면(= 한 번도 저장 안 됨) 새 엔티티 → persist */
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
