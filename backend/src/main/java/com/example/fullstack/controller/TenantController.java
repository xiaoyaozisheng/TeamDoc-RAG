package com.example.fullstack.controller;

import com.example.fullstack.dto.TenantRequest;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.exception.ResourceAlreadyExistsException;
import com.example.fullstack.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "租户管理", description = "租户/团队空间管理（SYS-02）")
@PreAuthorize("hasAuthority('tenant:manage') or hasRole('ADMIN')")
public class TenantController {

    private final TenantRepository tenantRepository;

    @Operation(summary = "获取租户列表")
    @GetMapping
    public List<Map<String, Object>> list() {
        return tenantRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    @Operation(summary = "创建租户")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody TenantRequest request) {
        if (tenantRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("租户名称已存在");
        }
        return toMap(tenantRepository.save(Tenant.builder().name(request.getName()).build()));
    }

    @Operation(summary = "更新租户（改名/停用/启用）")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody TenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new com.example.fullstack.exception.ResourceNotFoundException("租户不存在"));
        // 改名查重：避免唯一约束冲突直接 500
        if (request.getName() != null && !request.getName().equals(tenant.getName())
                && tenantRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("租户名称已存在");
        }
        tenant.setName(request.getName());
        return toMap(tenantRepository.save(tenant));
    }

    @Operation(summary = "停用租户")
    @PatchMapping("/{id}/disable")
    public Map<String, Object> disable(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new com.example.fullstack.exception.ResourceNotFoundException("租户不存在"));
        tenant.setStatus(Tenant.TenantStatus.DISABLED);
        return toMap(tenantRepository.save(tenant));
    }

    @Operation(summary = "启用租户（恢复停用的租户）")
    @PatchMapping("/{id}/enable")
    public Map<String, Object> enable(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new com.example.fullstack.exception.ResourceNotFoundException("租户不存在"));
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        return toMap(tenantRepository.save(tenant));
    }

    private Map<String, Object> toMap(Tenant tenant) {
        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "status", tenant.getStatus().name());
    }
}
