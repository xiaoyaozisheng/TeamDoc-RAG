package com.example.fullstack.controller;

import com.example.fullstack.dto.ChangePasswordRequest;
import com.example.fullstack.dto.UpdateUserRequest;
import com.example.fullstack.dto.UserResponse;
import com.example.fullstack.entity.User;
import com.example.fullstack.service.AuthService;
import com.example.fullstack.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息管理接口")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }

    @Operation(summary = "自助修改密码（P1）", description = "验证原密码后更新；成功后撤销全部刷新令牌，所有设备需重新登录")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "修改成功"),
            @ApiResponse(responseCode = "400", description = "原密码不正确或新密码不合规"),
            @ApiResponse(responseCode = "401", description = "未认证")
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取用户列表", description = "管理员权限")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功获取用户列表"),
            @ApiResponse(responseCode = "403", description = "无权限访问")
    })
    @PreAuthorize("hasAuthority('admin:read') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "根据ID获取用户", description = "仅本人或拥有 user:read 权限的用户可访问；跨租户返回 404（防水平越权）")
    @PreAuthorize("hasAuthority('user:read') or hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(userService.getUserByIdForCaller(id, caller));
    }

    @Operation(summary = "更新用户信息", description = "管理员权限")
    @PreAuthorize("hasAuthority('user:update') or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "删除用户", description = "管理员权限")
    @PreAuthorize("hasAuthority('user:delete') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
