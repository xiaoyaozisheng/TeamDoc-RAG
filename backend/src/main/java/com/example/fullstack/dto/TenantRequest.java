package com.example.fullstack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRequest {
    @jakarta.validation.constraints.NotBlank(message = "租户名称不能为空")
    @jakarta.validation.constraints.Size(max = 100, message = "租户名称过长")
    private String name;
}
