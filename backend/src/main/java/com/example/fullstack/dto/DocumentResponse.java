package com.example.fullstack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String name;
    private String type;
    private String status;
    private String errorMsg;
    private Long sizeBytes;
    private String uploadedByEmail;
    private LocalDateTime createdAt;
}
