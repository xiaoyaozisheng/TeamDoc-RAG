package com.example.fullstack.config;

import com.example.fullstack.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * RLS 连接级租户注入（TEN-04）：包装 DataSource，每次从池中取得连接时，
 * 根据当前请求的租户上下文执行 SET app.current_tenant（无租户上下文则 RESET，
 * 防止连接归还后租户变量泄漏给后台任务）。
 *
 * 配合 db/postgres/001_init_vector_store.sql 中的 RLS 策略，构成应用层过滤之外的数据库级兜底。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rag.rls.enabled", havingValue = "true")
public class TenantRlsDataSourceConfig implements BeanPostProcessor {

    @Bean
    public com.example.fullstack.security.TenantRlsFilter tenantRlsFilter() {
        return new com.example.fullstack.security.TenantRlsFilter();
    }

    /** 过滤器在 Security 链内注册（SecurityConfig），禁用 Servlet 层自动注册避免执行两次 */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.example.fullstack.security.TenantRlsFilter>
    tenantRlsFilterRegistration(com.example.fullstack.security.TenantRlsFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<com.example.fullstack.security.TenantRlsFilter> registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            log.info("启用 RLS 租户连接注入（app.current_tenant）");
            return new TenantRlsDataSource(dataSource);
        }
        return bean;
    }

    static class TenantRlsDataSource implements DataSource {

        private final DataSource delegate;

        TenantRlsDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return prepare(delegate.getConnection());
        }

        private Connection prepare(Connection connection) throws SQLException {
            Long tenantId = TenantContextHolder.get();
            try (Statement statement = connection.createStatement()) {
                if (tenantId != null) {
                    // tenantId 来源于数据库自增主键，非自由文本；仍做白名单校验防注入
                    statement.execute("SET app.current_tenant = '" + tenantId.toString().replaceAll("[^0-9]", "") + "'");
                } else {
                    statement.execute("RESET app.current_tenant");
                }
            } catch (SQLException e) {
                // app.current_tenant 尚未定义（未执行 GUC 初始化）时 SET 会失败；RLS 未配置则忽略
                if (e.getMessage() != null && e.getMessage().contains("app.current_tenant")) {
                    log.debug("RLS 会话变量不可用: {}", e.getMessage());
                } else {
                    throw e;
                }
            }
            return connection;
        }

        // —— 以下全部委托 ——
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return prepare(delegate.getConnection(username, password));
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
