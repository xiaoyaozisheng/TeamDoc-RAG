package com.example.fullstack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequest {

    @NotBlank(message = "权限名称不能为空")
    @Size(min = 2, max = 100, message = "权限名称长度必须在2-100之间")
    private String name;

    @Size(max = 255, message = "描述长度不能超过255")
    private String description;

    @NotBlank(message = "资源不能为空")
    @Size(max = 50)
    private String resource;

    @NotBlank(message = "操作不能为空")
    @Size(max = 50)
    private String action;
}
