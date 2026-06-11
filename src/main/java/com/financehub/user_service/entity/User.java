package com.financehub.user_service.entity;

import com.financehub.user_service.enums.Title;
import com.financehub.user_service.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @SequenceGenerator(
            name = "user_seq",
            sequenceName = "user_sequence",
            allocationSize = 1
    )
    @EmbeddedId
    private UserId userId;

    // Personal details
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Title title;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "First name is required")
    private String firstName;

    @Column(length = 50)
    private String middleName;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "Last name is required")
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    // Contact details
    @Column(nullable = false, unique = false)
    @NotBlank(message = "Email is required")
    @Email(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$",
            message = "Must be a valid email address with a proper domain"
    )
    private String email;

    @Column(length = 20)
    @Pattern(
            regexp = "^\\+?[0-9]{10,15}$",
            message = "Phone number must be between 10 and 15 digits and can start with +"
    )
    private String phoneNumber;

    // Security
    @Column(nullable = false, length = 100)
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one special character"
    )
    private String password;

    @Column(nullable = false)
    private boolean emailVerified = false;

    // Account status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    // Effective dating
    @Column(name = "end_date")
    private LocalDateTime endDate;

    // Audit fields
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // JPA lifecycle hooks
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (userId != null && userId.getEffectiveDate() == null) {
            userId.setEffectiveDate(now);  // LocalDateTime.now() not LocalDate.now()
        }
        if (status == null) {
            status = UserStatus.PENDING_VERIFICATION;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}