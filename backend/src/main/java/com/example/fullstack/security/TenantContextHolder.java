package com.example.fullstack.security;

/**
 * 请求级租户上下文（RLS 兜底用）：TenantRlsFilter 从认证上下文取出租户 id，
 * 由 TenantRlsDataSource 在每次取得连接时注入 PostgreSQL 会话变量 app.current_tenant。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
