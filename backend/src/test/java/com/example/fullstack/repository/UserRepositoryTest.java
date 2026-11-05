package com.example.fullstack.repository;

import com.example.fullstack.entity.Permission;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
class UserRepositoryTest {

    @TestConfiguration
    static class PasswordEncoderTestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private com.example.fullstack.repository.TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Role testRole;
    private Permission testPermission;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(Tenant.builder().name("测试团队").build());

        testPermission = permissionRepository.save(Permission.builder()
                .name("user:read")
                .description("Read users")
                .resource("user")
                .action("read")
                .build());

        testRole = roleRepository.save(Role.builder()
                .name("ROLE_USER")
                .description("Regular user role")
                .permissions(Set.of(testPermission))
                .build());

        testUser = User.builder()
                .displayName("testuser")
                .email("test@example.com")
                .password(passwordEncoder.encode("password123"))
                .enabled(true)
                .tenant(tenant)
                .roles(Set.of(testRole))
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    void findByEmail_shouldReturnUser_whenEmailExists() {
        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("testuser");
        assertThat(found.get().getRoles()).hasSize(1);
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenEmailNotExists() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void findByDisplayName_shouldReturnUser_whenDisplayNameExists() {
        Optional<User> found = userRepository.findByDisplayName("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void existsByEmail_shouldReturnTrue_whenEmailExists() {
        boolean exists = userRepository.existsByEmail("test@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalse_whenEmailNotExists() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    void userRoles_shouldBeLoaded_withPermissions() {
        User user = userRepository.findByEmail("test@example.com").orElseThrow();

        assertThat(user.getRoles()).isNotEmpty();
        Role role = user.getRoles().iterator().next();
        assertThat(role.getPermissions()).isNotEmpty();
        assertThat(role.getPermissions().iterator().next().getName()).isEqualTo("user:read");
    }
}