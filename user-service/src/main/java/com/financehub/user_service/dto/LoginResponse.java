package com.financehub.user_service.dto;

import com.financehub.user_service.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private LocalDateTime expiresAt;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
}