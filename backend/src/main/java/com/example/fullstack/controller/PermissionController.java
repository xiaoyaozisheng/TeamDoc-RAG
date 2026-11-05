package com.example.fullstack.controller;

import com.example.fullstack.dto.PermissionRequest;
import com.example.fullstack.dto.PermissionResponse;
import com.example.fullstack.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限CRUD管理接口（管理员）")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "获取权限列表")
    @PreAuthorize("hasAuthority('permission:read') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @Operation(summary = "根据ID获取权限")
    @PreAuthorize("hasAuthority('permission:read') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @Operation(summary = "创建权限")
    @PreAuthorize("hasAuthority('permission:create') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.createPermission(request));
    }

    @Operation(summary = "更新权限")
    @PreAuthorize("hasAuthority('permission:update') or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponse> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.updatePermission(id, request));
    }

    @Operation(summary = "删除权限")
    @PreAuthorize("hasAuthority('permission:delete') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }
}
