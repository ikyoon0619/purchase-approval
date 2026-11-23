package com.example.purchase.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 메서드를 부르면 감사 로그를 남긴다. 서비스 코드에는 이 한 줄만 붙이고, 기록은 AuditAspect 한곳에서 한다.
 *
 * <pre>
 * &#64;Audited(action = AuditAction.APPROVE, target = "구매요청서", id = "#requestId")
 * public void approve(Long requestId, LoginUser user, String comment)
 *
 * &#64;Audited(action = AuditAction.CREATE, target = "구매요청서", id = "#result")   // 새로 만든 id는 반환값
 * public Long create(...)
 * </pre>
 *
 * <p>주의: 같은 클래스 안에서 this.method()로 부르면 기록되지 않는다 (스프링 AOP는 프록시를 거칠 때만 동작).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    /** 대상 종류 (화면에 그대로 보인다): 구매요청서, 발주서, 품목 … */
    String target();

    /** 대상 id를 꺼내는 SpEL. 파라미터는 #이름, 반환값은 #result. 비우면 기록 안 함 */
    String id() default "";
}
