package com.financehub.user_service.entity;

import com.financehub.user_service.enums.*;
import com.financehub.user_service.repository.UserRepository;
import com.financehub.user_service.service.UserService;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity representing a user in the Personal Finance Hub system.
 *
 * Implements effective dating — rather than overwriting records on update,
 * a new version is inserted with a new effectiveDate and the previous version
 * is closed by setting its endDate. This provides a complete immutable audit
 * history of all changes.
 *
 * The current active record for any user is identified by endDate IS NULL.
 * All historical versions are retained and queryable via point in time queries.
 *
 * Passwords are never stored in plain text — BCrypt hashing is applied
 * in the service layer before persistence.
 *
 * @see UserId
 * @see UserService
 * @see UserRepository
 */
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

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

    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // JPA lifecycle hooks
    /**
     * Sets audit timestamps and effective date on initial persist.
     * If effectiveDate is not explicitly set, defaults to current timestamp.
     * If status is not set, defaults to PENDING_VERIFICATION.
     * If role is not set, defaults to ROLE_USER.
     *
     * Note: createdAt is marked updatable = false — once set it is never
     * overwritten, providing a reliable record of when the version was created.
     */
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

        if (role == null) {
            role = Role.ROLE_USER;
        }
    }
    
    /**
     * Updates the updatedAt audit timestamp on every subsequent save.
     * createdAt and createdBy are intentionally not updated here — they
     * represent the original creation of this version and must remain immutable.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}