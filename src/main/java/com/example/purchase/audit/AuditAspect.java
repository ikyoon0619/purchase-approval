package com.example.purchase.audit;

import com.example.purchase.audit.domain.AuditLog;
import com.example.purchase.audit.service.AuditLogWriter;
import com.example.purchase.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * &#64;Audited가 붙은 메서드 앞뒤에서 감사 로그를 남긴다 (OPS-01).
 *
 * <p><b>"성공"은 커밋이 끝난 뒤에야 확정된다.</b> 처음엔 메서드가 예외 없이 돌아오면 바로 SUCCESS로 썼는데,
 * 낙관적 락 충돌처럼 <i>커밋할 때</i> 실패하는 경우가 성공으로 남았다 (docs/troubleshooting/08). 그래서
 * <ol>
 *   <li>@Order(1): 트랜잭션 어드바이스(기본 순서 LOWEST_PRECEDENCE)보다 바깥에서 돈다 → proceed()가 돌아왔으면
 *       서비스 트랜잭션은 이미 커밋됐다. 커밋 실패는 proceed()에서 예외로 올라온다.
 *       HIGHEST_PRECEDENCE로 두면 안 된다: 스프링이 맨 앞에 두는 ExposeInvocationInterceptor보다 먼저 돌게 되어
 *       "Required to bind 2 arguments, but only bound 1 (JoinPointMatch was NOT bound in invocation)"로 실패한다.</li>
 *   <li>그래도 호출한 쪽이 더 큰 트랜잭션을 열어 두었다면(다른 서비스, 배치, 테스트) 아직 커밋 전이다.
 *       그때는 트랜잭션이 끝난 뒤(afterCompletion) 커밋됐는지 보고 성공/실패를 쓴다.</li>
 *   <li>저장은 AuditLogWriter(REQUIRES_NEW)라 업무가 롤백돼도 기록은 남는다.</li>
 * </ol>
 * 기록 저장이 실패해도 업무는 그대로 진행한다 (감사 로그 때문에 결재가 막히면 안 된다).
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class AuditAspect {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private final AuditLogWriter writer;

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Who who = who(pjp.getArgs());
        String ip = clientIp();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            save(audited, who, targetId(pjp, audited, null), false, e.getClass().getSimpleName() + ": " + e.getMessage(), ip);
            throw e;
        }
        String targetId = targetId(pjp, audited, result);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 바깥 트랜잭션이 아직 열려 있다 → 결과는 그 트랜잭션이 끝나야 안다
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    boolean committed = status == STATUS_COMMITTED;
                    save(audited, who, targetId, committed, committed ? null : "트랜잭션이 커밋되지 않았습니다 (롤백)", ip);
                }
            });
        } else {
            save(audited, who, targetId, true, null, ip);
        }
        return result;
    }

    private void save(Audited audited, Who who, String targetId, boolean success, String detail, String ip) {
        try {
            writer.write(new AuditLog(who.id(), who.loginId(), audited.action(), audited.target(), targetId,
                    success, detail, ip, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.error("감사 로그를 저장하지 못했습니다: {} {} {}", audited.action(), audited.target(), targetId, e);
        }
    }

    private String targetId(ProceedingJoinPoint pjp, Audited audited, Object result) {
        if (!StringUtils.hasText(audited.id())) {
            return null;
        }
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            var ctx = new MethodBasedEvaluationContext(pjp.getTarget(), sig.getMethod(), pjp.getArgs(), NAMES);
            ctx.setVariable("result", result);
            Object v = PARSER.parseExpression(audited.id()).getValue(ctx);
            return v == null ? null : String.valueOf(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 누가: 파라미터에 LoginUser가 있으면 그 사람, 없으면 로그인 정보, 그것도 없으면 system(배치·스케줄러) */
    private Who who(Object[] args) {
        LoginUser user = Arrays.stream(args).filter(LoginUser.class::isInstance).map(LoginUser.class::cast)
                .findFirst().orElse(null);
        if (user == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser u) {
                user = u;
            }
        }
        return user == null ? new Who(null, "system") : new Who(user.getId(), user.getLoginId());
    }

    private String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");   // 11단계: 로드밸런서·Nginx 뒤에서는 원래 IP가 여기에
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private record Who(Long id, String loginId) {
    }
}
