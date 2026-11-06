package com.example.fullstack.service;

import com.example.fullstack.dto.PermissionResponse;
import com.example.fullstack.dto.RoleResponse;
import com.example.fullstack.dto.UpdateUserRequest;
import com.example.fullstack.dto.UserResponse;
import com.example.fullstack.entity.Permission;
import com.example.fullstack.entity.Role;
import com.example.fullstack.entity.User;
import com.example.fullstack.exception.ResourceAlreadyExistsException;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.repository.RoleRepository;
import com.example.fullstack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        return mapToUserResponse(user);
    }

    /**
     * 供 GET /users/{id} 使用：防水平越权——user:read 持有者也只能查看本租户用户，
     * 非本租户返回 404（不泄露存在性）；ADMIN 可看全部。
     */
    public UserResponse getUserByIdForCaller(Long id, User caller) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        boolean isAdmin = caller.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
        boolean sameTenant = caller.getTenant() != null && user.getTenant() != null
                && caller.getTenant().getId().equals(user.getTenant().getId());
        if (!isAdmin && !sameTenant) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return mapToUserResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        if (request.getDisplayName() != null) {
            if (!request.getDisplayName().equals(user.getDisplayName())
                    && userRepository.existsByDisplayName(request.getDisplayName())) {
                throw new ResourceAlreadyExistsException("用户名已被使用");
            }
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        if (request.getRoleIds() != null) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(request.getRoleIds()));
            user.setRoles(roles);
        }

        return mapToUserResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapToUserResponse(User user) {
        List<RoleResponse> roleResponses = user.getRoles().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());

        Set<String> permissions = user.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getDisplayName())
                .email(user.getEmail())
                .roles(roleResponses)
                .permissions(permissions)
                .build();
    }

    private RoleResponse mapToRoleResponse(Role role) {
        List<PermissionResponse> permissionResponses = role.getPermissions().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissionResponses)
                .build();
    }

    private PermissionResponse mapToPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .resource(permission.getResource())
                .action(permission.getAction())
                .build();
    }
}
