package com.example.fullstack.config;

import com.example.fullstack.entity.Permission;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import com.example.fullstack.repository.PermissionRepository;
import com.example.fullstack.repository.RoleRepository;
import com.example.fullstack.repository.TenantRepository;
import com.example.fullstack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 资源 -> 操作 -> 描述 */
    private static final Map<String, Map<String, String>> DEFAULT_PERMISSIONS = Map.ofEntries(
            Map.entry("user", Map.of("read", "查看用户信息", "create", "创建用户", "update", "更新用户", "delete", "删除用户")),
            Map.entry("role", Map.of("read", "查看角色", "create", "创建角色", "update", "更新角色", "delete", "删除角色")),
            Map.entry("permission", Map.of("read", "查看权限", "create", "创建权限", "update", "更新权限", "delete", "删除权限")),
            Map.entry("document", Map.of("read", "查看文档", "create", "上传文档", "update", "更新文档", "delete", "删除文档")),
            Map.entry("chat", Map.of("ask", "知识库问答")),
            Map.entry("eval", Map.of("manage", "管理评估集与评估任务")),
            Map.entry("tenant", Map.of("manage", "管理租户")),
            Map.entry("admin", Map.of("read", "查看管理信息"))
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing permissions...");
        Map<String, Permission> permissions = new HashMap<>();
        DEFAULT_PERMISSIONS.forEach((resource, actions) -> actions.forEach((action, description) -> {
            String name = resource + ":" + action;
            Permission permission = permissionRepository.findByName(name)
                    .orElseGet(() -> {
                        Permission created = permissionRepository.save(Permission.builder()
                                .name(name)
                                .description(description)
                                .resource(resource)
                                .action(action)
                                .build());
                        log.info("Created permission: {}", name);
                        return created;
                    });
            permissions.put(name, permission);
        }));

        Tenant defaultTenant = tenantRepository.findByName("默认团队")
                .orElseGet(() -> {
                    log.info("Creating default tenant...");
                    return tenantRepository.save(Tenant.builder().name("默认团队").build());
                });

        log.info("Initializing roles...");
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("普通成员")
                        .build()));
        userRole.setPermissions(new HashSet<>(Set.of(
                permissions.get("user:read"),
                permissions.get("document:read"),
                permissions.get("document:create"),
                permissions.get("chat:ask")
        )));
        userRole = roleRepository.save(userRole);

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ADMIN")
                        .description("系统管理员")
                        .build()));
        // ADMIN 拥有全部权限；即便 @PreAuthorize 未覆盖到，接口层还有 hasRole('ADMIN') 兜底
        adminRole.setPermissions(new HashSet<>(permissions.values()));
        adminRole = roleRepository.save(adminRole);

        createUserIfAbsent("admin", "admin@example.com", "admin123", adminRole, defaultTenant);
        createUserIfAbsent("user", "user@example.com", "user123", userRole, defaultTenant);

        log.info("Data initialization completed successfully!");
    }

    private void createUserIfAbsent(String displayName, String email, String password, Role role, Tenant tenant) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        log.info("Creating default user: {}", email);
        userRepository.save(User.builder()
                .displayName(displayName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .enabled(true)
                .tenant(tenant)
                .roles(Set.of(role))
                .build());
    }
}
