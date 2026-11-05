package com.example.fullstack.service;

import com.example.fullstack.entity.AuditLog;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 审计日志（SYS-04）：登录、权限变更、文档删除等关键动作。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Tenant tenant, Long userId, String action, String detail) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .tenant(tenant)
                    .userId(userId)
                    .action(action)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", action, e);
        }
    }
}
