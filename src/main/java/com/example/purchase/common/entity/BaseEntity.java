package com.example.purchase.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 시각에 더해 "누가" 만들고 고쳤는지까지 남긴다 (요구사항 NFR-02).
 * 값은 {@link com.example.purchase.config.JpaConfig}의 AuditorAware가 넣어 준다.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(updatable = false, length = 30)
    private String createdBy;

    @LastModifiedBy
    @Column(length = 30)
    private String updatedBy;
}
