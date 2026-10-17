package com.shortvideoscripagent.xhsagentyunying.infra.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 Controller 方法为"需要审计"。
 * <p>
 * 由 {@link AuditAspect} 切面处理，方法正常返回时记录 success，抛 {@code BusinessException} 时记录 failure。
 * 切面仅审计元数据（action + resourceType + 路径参数），不审计入参载荷（避免误写 PII）。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 操作语义，如 "workspace.create" / "member.invite" */
    String action();

    /** 资源类型，如 "workspace" / "member" */
    String resourceType();

    /**
     * SpEL 表达式，从参数中提取 resourceId。
     * 例：{@code "#id"} / {@code "#workspaceId + ':' + #userId"}
     * 留空则使用路径变量 {@code id} / {@code workspaceId}，都没有则用 "n/a"。
     */
    String resourceId() default "";
}
