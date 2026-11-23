package com.example.purchase.master.nts;

/** 국세청 API를 부르지 못했거나 응답이 이상할 때. 호출한 쪽은 "확인 안 됨"으로 저장한다 (BR-09) */
public class NtsApiException extends RuntimeException {
    public NtsApiException(String message) {
        super(message);
    }

    public NtsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
