package com.example.fullstack.controller;

import com.example.fullstack.dto.RoleRequest;
import com.example.fullstack.dto.RoleResponse;
import com.example.fullstack.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色CRUD管理接口（管理员）")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表")
    @PreAuthorize("hasAuthority('role:read') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<RoleResponse>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @Operation(summary = "根据ID获取角色")
    @PreAuthorize("hasAuthority('role:read') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @Operation(summary = "创建角色")
    @PreAuthorize("hasAuthority('role:create') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(request));
    }

    @Operation(summary = "更新角色")
    @PreAuthorize("hasAuthority('role:update') or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @Operation(summary = "删除角色")
    @PreAuthorize("hasAuthority('role:delete') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
