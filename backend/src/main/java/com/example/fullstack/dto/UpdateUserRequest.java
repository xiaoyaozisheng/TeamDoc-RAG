package com.example.fullstack.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    private String displayName;

    /** 管理员重置密码（6-64 位）；为空则不修改 */
    @Size(min = 6, max = 64, message = "密码长度必须在6-64之间")
    private String password;

    private Boolean enabled;

    private Set<Long> roleIds;
}
