package com.pavankumar.shopnestecommercebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CategoryResponse implements Serializable {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
