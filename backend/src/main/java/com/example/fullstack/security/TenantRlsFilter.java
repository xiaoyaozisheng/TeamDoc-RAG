package com.example.fullstack.security;

import com.example.fullstack.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * RLS 租户上下文过滤器（TEN-04）：在 JWT 认证之后运行，把当前租户放入 ThreadLocal，
 * 由 TenantRlsDataSource 在取得连接时执行 SET app.current_tenant。
 * 由 TenantRlsDataSourceConfig 条件化声明（仅 postgres Profile），并在 Security 链内注册。
 */
@RequiredArgsConstructor
public class TenantRlsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User user
                    && user.getTenant() != null) {
                TenantContextHolder.set(user.getTenant().getId());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
