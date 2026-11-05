package com.example.fullstack.service;

import com.example.fullstack.dto.PermissionRequest;
import com.example.fullstack.dto.PermissionResponse;
import com.example.fullstack.entity.Permission;
import com.example.fullstack.exception.ResourceAlreadyExistsException;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("权限不存在"));
        return mapToPermissionResponse(permission);
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        if (permissionRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("权限名称已存在");
        }

        Permission permission = Permission.builder()
                .name(request.getName())
                .description(request.getDescription())
                .resource(request.getResource())
                .action(request.getAction())
                .build();

        return mapToPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("权限不存在"));

        if (request.getName() != null) {
            if (!permission.getName().equals(request.getName())
                    && permissionRepository.existsByName(request.getName())) {
                throw new ResourceAlreadyExistsException("权限名称已存在");
            }
            permission.setName(request.getName());
        }

        if (request.getDescription() != null) {
            permission.setDescription(request.getDescription());
        }
        if (request.getResource() != null) {
            permission.setResource(request.getResource());
        }
        if (request.getAction() != null) {
            permission.setAction(request.getAction());
        }

        return mapToPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("权限不存在");
        }
        permissionRepository.deleteById(id);
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
