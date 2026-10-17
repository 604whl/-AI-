package com.shortvideoscripagent.xhsagentyunying.infra.audit;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计切面：拦截 {@link Audited} 注解方法，方法返回后异步落 audit_event。
 * <p>
 * 失败语义：
 * <ul>
 *   <li>正常返回 → 写 success 审计</li>
 *   <li>抛 {@link BusinessException} → 写 failure 审计（带错误码）</li>
 *   <li>抛其它异常 → 不审计（避免审计链上抛业务异常外的错误）</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditWriter auditWriter;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String resourceId = resolveResourceId(pjp, audited);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("class", pjp.getTarget().getClass().getSimpleName());

        try {
            Object result = pjp.proceed();
            auditWriter.record(audited.action(), audited.resourceType(), resourceId, metadata);
            return result;
        } catch (BusinessException be) {
            metadata.put("errorCode", be.getCode());
            auditWriter.recordFailure(audited.action(), audited.resourceType(), resourceId,
                    be.getCode(), be.getMessage(), metadata);
            throw be;
        }
    }

    private String resolveResourceId(ProceedingJoinPoint pjp, Audited audited) {
        if (audited.resourceId() != null && !audited.resourceId().isBlank()) {
            try {
                MethodSignature sig = (MethodSignature) pjp.getSignature();
                Method method = sig.getMethod();
                String[] names = NAME_DISCOVERER.getParameterNames(method);
                Object[] args = pjp.getArgs();
                EvaluationContext ctx = new StandardEvaluationContext();
                if (names != null) {
                    for (int i = 0; i < names.length; i++) {
                        ctx.setVariable(names[i], args[i]);
                    }
                }
                Expression exp = PARSER.parseExpression(audited.resourceId());
                Object value = exp.getValue(ctx);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ex) {
                log.debug("SpEL resolveResourceId failed: {}", ex.getMessage());
            }
        }
        // fallback：尝试从路径变量
        Object[] args = pjp.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        return "n/a";
    }
}
