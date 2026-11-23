package com.example.purchase.request.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 첨부파일 정보. 파일 자체는 디스크(app.upload-dir)에, DB에는 이름과 크기만.
 * 서버에는 UUID 이름으로 저장한다: 같은 이름 덮어쓰기, ../ 같은 경로 조작, 한글·특수문자 문제를 한 번에 피한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private PurchaseRequest request;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 100)
    private String storedName;

    @Column(nullable = false)
    private long size;

    @Column(length = 100)
    private String contentType;

    Attachment(PurchaseRequest request, String originalName, String storedName, long size, String contentType) {
        this.request = request;
        this.originalName = originalName;
        this.storedName = storedName;
        this.size = size;
        this.contentType = contentType;
    }
}
