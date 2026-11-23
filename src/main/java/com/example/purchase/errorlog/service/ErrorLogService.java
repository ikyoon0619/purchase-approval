package com.example.purchase.errorlog.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.errorlog.domain.ErrorLog;
import com.example.purchase.errorlog.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    /**
     * 예외를 저장하고 번호를 돌려준다. REQUIRES_NEW: 실패한 업무 트랜잭션 안에서 불려도 이 기록은 따로 커밋된다.
     * 저장 자체가 실패하면(DB가 죽었을 때 등) null. 오류 처리 중에 또 예외를 던지면 원래 오류 화면도 못 보여 준다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(String source, String loginId, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return errorLogRepository.save(new ErrorLog(source, loginId, e, sw.toString(), LocalDateTime.now())).getId();
        } catch (RuntimeException saveFailed) {
            log.error("에러 로그를 저장하지 못했습니다", saveFailed);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<ErrorLogRow> search(String keyword, Pageable pageable) {
        return errorLogRepository.search(StringUtils.hasText(keyword) ? keyword.trim() : null, pageable);
    }

    @Transactional(readOnly = true)
    public ErrorLog get(Long id) {
        return errorLogRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
