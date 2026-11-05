package com.example.fullstack.service;

import com.example.fullstack.dto.PermissionResponse;
import com.example.fullstack.dto.RoleRequest;
import com.example.fullstack.dto.RoleResponse;
import com.example.fullstack.entity.Permission;
import com.example.fullstack.entity.Role;
import com.example.fullstack.exception.ResourceAlreadyExistsException;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.repository.PermissionRepository;
import com.example.fullstack.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在"));
        return mapToRoleResponse(role);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("角色名称已存在");
        }

        Set<Permission> permissions = request.getPermissionIds() != null
                ? permissionRepository.findAllById(request.getPermissionIds()).stream().collect(Collectors.toSet())
                : Set.of();

        Role role = Role.builder()
                .name("ROLE_" + request.getName().toUpperCase())
                .description(request.getDescription())
                .permissions(permissions)
                .build();

        return mapToRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在"));

        if (request.getName() != null) {
            String newName = "ROLE_" + request.getName().toUpperCase();
            if (!role.getName().equals(newName) && roleRepository.existsByName(newName)) {
                throw new ResourceAlreadyExistsException("角色名称已存在");
            }
            role.setName(newName);
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        return mapToRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("角色不存在");
        }
        roleRepository.deleteById(id);
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
