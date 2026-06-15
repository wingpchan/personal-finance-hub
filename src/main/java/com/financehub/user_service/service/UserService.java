package com.financehub.user_service.service;

import com.financehub.user_service.dto.LoginRequest;
import com.financehub.user_service.dto.LoginResponse;
import com.financehub.user_service.enums.Title;
import com.financehub.user_service.enums.UserStatus;
import com.financehub.user_service.enums.Role;
import com.financehub.user_service.entity.User;
import com.financehub.user_service.entity.UserId;
import com.financehub.user_service.enums.UserStatusReason;
import com.financehub.user_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Business logic layer for user management operations.
 *
 * Implements effective dating — updates and deletes never overwrite existing
 * records. Updates close the current version and insert a new one.
 * Deletes set endDate on the current version (soft delete).
 *
 * All password operations use BCrypt hashing via Spring Security Crypto.
 * Plain text passwords are never persisted or logged.
 *
 * Password history is validated across all previous versions using the
 * effective dating audit trail — no separate password history table required.
 *
 * @see UserRepository
 * @see JwtService
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.firstName}")
    private String adminFirstName;

    @Value("${admin.lastName}")
    private String adminLastName;

    public UserService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Authenticates a user and returns a JWT token on success.
     * Email lookup is case insensitive — normalised before querying.
     *
     * Deliberately uses the same error message for both invalid email
     * and invalid password to prevent user enumeration attacks — an
     * attacker cannot determine whether an email address is registered.
     *
     * Account status is checked before password validation — suspended,
     * inactive and closed accounts are rejected with specific messages.
     *
     * @param loginRequest the login credentials
     * @return LoginResponse containing JWT token, expiry and basic user details
     * @throws RuntimeException if credentials are invalid or account is not active
     */
    public LoginResponse login(LoginRequest loginRequest) {
        // Find current active user by email
        User user = userRepository.findCurrentByEmail(loginRequest.getEmail().toLowerCase())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Check account status
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new RuntimeException("Account is suspended");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new RuntimeException("Account is inactive");
        }
        if (user.getStatus() == UserStatus.CLOSED) {
            throw new RuntimeException("Account is closed");
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new RuntimeException("Email not yet verified");
        }

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Generate JWT token
        String token = jwtService.generateToken(
                user.getUserId().getId(),
                user.getEmail(),
                user.getRole()
        );

        // Build and return response
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.extractExpiration(token),
                user.getUserId().getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus()
        );
    }

    /**
     * Registers an admin user with ACTIVE status, ROLE_ADMIN and emailVerified = true.
     * Admin users bypass the email verification flow.
     * Should only be called internally — the corresponding endpoint is
     * protected by ROLE_ADMIN.
     *
     * @param user the admin user details
     * @param createdBy audit field identifying who initiated the registration
     * @return the saved admin User entity
     * @throws RuntimeException if the email address is already registered
     */
    public User registerAdmin(User user, String createdBy) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered: " + user.getEmail());
        }

        Long nextId = userRepository.getNextUserId();
        UserId userId = new UserId();
        userId.setId(nextId);
        userId.setEffectiveDate(LocalDateTime.now());
        user.setUserId(userId);

        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(Role.ROLE_ADMIN);
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    /**
     * Registers a new user with PENDING_VERIFICATION status and ROLE_USER.
     * Email is normalised to lowercase before storage.
     * Password is BCrypt hashed before storage.
     * Effective date is set to current timestamp.
     *
     * @param user the user details from the registration request
     * @param createdBy audit field identifying who initiated the registration
     * @return the saved User entity with generated ID and composite key
     * @throws RuntimeException if the email address is already registered
     */
    public User registerUser(User user, String createdBy) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered: " + user.getEmail());
        }

        // Generate next ID from sequence
        Long nextId = userRepository.getNextUserId();

        // Set up composite key
        UserId userId = new UserId();
        userId.setId(nextId);
        userId.setEffectiveDate(LocalDateTime.now());
        user.setUserId(userId);

        // Set audit fields
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        // Normalise email to lowercase before storing
        user.setEmail(user.getEmail().toLowerCase());
        // Default role
        user.setRole(Role.ROLE_USER);
        // Default status
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        // Hash password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    // Get current active record by id
    public Optional<User> findCurrentById(Long id) {
        return userRepository.findCurrentById(id);
    }

    // Get full version history for a user
    public List<User> findAllVersionsById(Long id) {
        return userRepository.findAllVersionsById(id);
    }

    // Find current record by email
    public Optional<User> findCurrentByEmail(String email) {
        return userRepository.findCurrentByEmail(email);
    }

    // Get all users
    public List<User> findAll(boolean activeOnly) {
        return activeOnly
                ? userRepository.findAllCurrentUsers()
                : userRepository.findLatestVersionAllUsers();
    }

    // Search by first name
    public List<User> searchByFirstName(String firstName, boolean activeOnly) {
        return activeOnly
                ? userRepository.findCurrentByFirstNameContaining(firstName)
                : userRepository.findLatestVersionByFirstNameContaining(firstName);
    }

    // Search by last name
    public List<User> searchByLastName(String lastName, boolean activeOnly) {
        return activeOnly
                ? userRepository.findCurrentByLastNameContaining(lastName)
                : userRepository.findLatestVersionByLastNameContaining(lastName);
    }

    public List<User> searchByName(String name, boolean activeOnly) {
        return activeOnly
                ? userRepository.findCurrentByName(name)
                : userRepository.findLatestVersionByName(name);
    }

    public List<User> searchByFirstAndLastName(
            String firstName, String lastName, boolean activeOnly) {
        return activeOnly
                ? userRepository.findCurrentByFirstNameAndLastNameContaining(firstName, lastName)
                : userRepository.findLatestVersionByFirstNameAndLastNameContaining(firstName, lastName);
    }

    // Point in time query
    public List<User> findAtPointInTime(Long id, LocalDateTime queryDate) {
        return userRepository.findByIdAtPointInTime(id, queryDate);
    }

    /**
     * Updates a user by closing the current version and inserting a new one.
     * The new version effectiveDate is set to 1 microsecond after the endDate
     * of the closed version to avoid boundary clash in point in time queries.
     *
     * Email uniqueness is validated against current active records only —
     * the same email can exist on historical closed records.
     *
     * Password is rehashed on every update — the incoming password is
     * treated as plain text regardless of source.
     *
     * @param id the user's generated sequence ID
     * @param updatedUser the updated user details
     * @param updatedBy audit field identifying who made the change
     * @return the newly created User version
     * @throws RuntimeException if user not found or email already in use
     */
    public User updateUser(Long id, User updatedUser, String updatedBy) {
        // Find current active record
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        // Check if new email is already in use by a different active user
        // Normalise the updateUser email address before check and later store
        String lowerUpdateUserEmail = updatedUser.getEmail().toLowerCase();
        if (!lowerUpdateUserEmail.equals(currentUser.getEmail())
                && userRepository.existsByEmail(lowerUpdateUserEmail)) {
            throw new RuntimeException("Email already in use: " + updatedUser.getEmail());
        }

        // Close off current record with exact timestamp
        LocalDateTime now = LocalDateTime.now();
        currentUser.setEndDate(now);
        currentUser.setUpdatedBy(updatedBy);
        userRepository.save(currentUser);

        // Create new version with nanosecond after to avoid boundary clash
        UserId newUserId = new UserId();
        newUserId.setId(id);
        newUserId.setEffectiveDate(now.plusNanos(1000)); // guaranteed different from endDate
        updatedUser.setUserId(newUserId);
        updatedUser.setCreatedBy(currentUser.getCreatedBy());
        updatedUser.setUpdatedBy(updatedBy);
        // Already normalised email to lowercase
        updatedUser.setEmail(lowerUpdateUserEmail);
        updatedUser.setEmailVerified(currentUser.isEmailVerified());

        // Hash password
        updatedUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));

        return userRepository.save(updatedUser);
    }

    /**
     * Soft deletes a user by setting endDate on the current active record.
     * The record is never physically removed — full history is preserved.
     * A deleted user can be reinstated by an admin via reinstateUser().
     *
     * @param id the user's generated sequence ID
     * @param deletedBy audit field identifying who performed the deletion
     * @throws RuntimeException if no active user found with the given ID
     */
    public void deleteUser(Long id, String deletedBy) {
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        currentUser.setEndDate(LocalDateTime.now());
        currentUser.setUpdatedBy(deletedBy);
        userRepository.save(currentUser);
    }

    /**
     * Reinstates a soft deleted user by creating a new active version
     * based on the most recent closed version.
     * Only available to ROLE_ADMIN.
     *
     * Leverages the effective dating model — reinstatement is simply
     * a new version insert with endDate = null, consistent with all
     * other update operations.
     *
     * @param id the user's generated sequence ID
     * @param reinstatedBy audit field identifying who performed the reinstatement
     * @return the newly created active User version
     * @throws RuntimeException if user not found or user is already active
     */
    public User reinstateUser(Long id, String reinstatedBy) {
        // Get the latest version regardless of active status
        List<User> versions = userRepository.findAllVersionsById(id);
        if (versions.isEmpty()) {
            throw new RuntimeException("User not found with ID: " + id);
        }

        // Check it's actually deleted
        User latestVersion = versions.get(0);
        if (latestVersion.getEndDate() == null) {
            throw new RuntimeException("User is already active, no reinstatement needed");
        }

        // Create new active version based on latest
        LocalDateTime now = LocalDateTime.now();
        UserId newUserId = new UserId();
        newUserId.setId(id);
        newUserId.setEffectiveDate(now);

        User reinstatedUser = new User();
        reinstatedUser.setUserId(newUserId);
        reinstatedUser.setTitle(latestVersion.getTitle());
        reinstatedUser.setFirstName(latestVersion.getFirstName());
        reinstatedUser.setMiddleName(latestVersion.getMiddleName());
        reinstatedUser.setLastName(latestVersion.getLastName());
        reinstatedUser.setDateOfBirth(latestVersion.getDateOfBirth());
        reinstatedUser.setEmail(latestVersion.getEmail());
        reinstatedUser.setPhoneNumber(latestVersion.getPhoneNumber());
        reinstatedUser.setPassword(latestVersion.getPassword());
        reinstatedUser.setEmailVerified(latestVersion.isEmailVerified());
        reinstatedUser.setStatus(UserStatus.ACTIVE);
        reinstatedUser.setRole(latestVersion.getRole());
        reinstatedUser.setCreatedBy(latestVersion.getCreatedBy());
        reinstatedUser.setUpdatedBy(reinstatedBy);

        return userRepository.save(reinstatedUser);
    }

    // Verify email
    public User verifyEmail(Long id, String updatedBy) {
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        currentUser.setEmailVerified(true);
        currentUser.setStatus(UserStatus.ACTIVE);
        currentUser.setUpdatedBy(updatedBy);
        return userRepository.save(currentUser);
    }

    // Update status
    // A cleaner long-term pattern is Lombok's @Builder(toBuilder = true) - do that on later refractoring.
    public User updateStatus(Long id, UserStatus status, UserStatusReason statusReason, String updatedBy) {
        // Find current active record
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        // Close off current record with exact timestamp
        LocalDateTime now = LocalDateTime.now();
        currentUser.setEndDate(now);
        currentUser.setUpdatedBy(updatedBy);
        userRepository.save(currentUser);

        // Create new version with nanosecond after to avoid boundary clash
        UserId newUserId = new UserId();
        newUserId.setId(id);
        newUserId.setEffectiveDate(now.plusNanos(1000));

        User newVersion = new User();
        newVersion.setUserId(newUserId);
        newVersion.setRole(currentUser.getRole());
        newVersion.setTitle(currentUser.getTitle());
        newVersion.setFirstName(currentUser.getFirstName());
        newVersion.setMiddleName(currentUser.getMiddleName());
        newVersion.setLastName(currentUser.getLastName());
        newVersion.setDateOfBirth(currentUser.getDateOfBirth());
        newVersion.setEmail(currentUser.getEmail());
        newVersion.setPhoneNumber(currentUser.getPhoneNumber());
        newVersion.setPassword(currentUser.getPassword());
        newVersion.setEmailVerified(currentUser.isEmailVerified());
        newVersion.setResetToken(currentUser.getResetToken());
        newVersion.setResetTokenExpiry(currentUser.getResetTokenExpiry());
        newVersion.setCreatedBy(currentUser.getCreatedBy());
        newVersion.setUpdatedBy(updatedBy);

        // New values for this version
        newVersion.setStatus(status);
        newVersion.setStatusReason(statusReason);

        return userRepository.save(newVersion);
    }

    /**
     * Creates a default admin user on application startup if none exists.
     * Credentials are loaded from environment variables via Spring profiles —
     * never hardcoded in source code.
     *
     * In production: after seeding, a human admin should create their own
     * account and the seeded admin should be deleted. Environment variables
     * should then be rotated.
     *
     * Uses existsByEmailAny rather than existsByEmail to check across all
     * records including historical, preventing re-seeding after deletion.
     */
    public void seedAdminUser() {
        if (!userRepository.existsByEmailAny(adminEmail)) {
            User admin = new User();
            admin.setTitle(Title.MR);
            admin.setFirstName(adminFirstName);
            admin.setLastName(adminLastName);
            admin.setDateOfBirth(java.time.LocalDate.of(1980, 1, 1));
            admin.setEmail(adminEmail);
            admin.setPhoneNumber("+441234567890");
            admin.setPassword(adminPassword);
            admin.setRole(Role.ROLE_ADMIN);
            registerAdmin(admin, "system");
        }
    }

    /**
     * Changes a user's password by creating a new version with the updated hash.
     * Validates the current password before proceeding.
     * Validates the new password has not been used in any previous version.
     *
     * Creates a new effective dated version rather than updating in place —
     * consistent with the effective dating model and provides a complete
     * audit trail of password changes.
     *
     * @param id the user's generated sequence ID
     * @param currentPassword the user's current plain text password for verification
     * @param newPassword the new plain text password to hash and store
     * @param updatedBy audit field identifying who made the change
     * @throws RuntimeException if current password incorrect or new password previously used
     */
    public void changePassword(Long id, String currentPassword,
                               String newPassword, String updatedBy) {
        // Find current active user
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        // Verify current password is correct
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Ensure new password not previously used
        validatePasswordNotPreviouslyUsed(id, newPassword);

        // Create new version with updated password
        LocalDateTime now = LocalDateTime.now();
        UserId newUserId = new UserId();
        newUserId.setId(id);
        newUserId.setEffectiveDate(now.plusNanos(1000));

        // Close current record
        currentUser.setEndDate(now);
        currentUser.setUpdatedBy(updatedBy);
        userRepository.save(currentUser);

        // Create new version
        User updatedUser = new User();
        updatedUser.setUserId(newUserId);
        updatedUser.setTitle(currentUser.getTitle());
        updatedUser.setFirstName(currentUser.getFirstName());
        updatedUser.setMiddleName(currentUser.getMiddleName());
        updatedUser.setLastName(currentUser.getLastName());
        updatedUser.setDateOfBirth(currentUser.getDateOfBirth());
        updatedUser.setEmail(currentUser.getEmail());
        updatedUser.setPhoneNumber(currentUser.getPhoneNumber());
        updatedUser.setPassword(passwordEncoder.encode(newPassword));
        updatedUser.setEmailVerified(currentUser.isEmailVerified());
        updatedUser.setStatus(currentUser.getStatus());
        updatedUser.setRole(currentUser.getRole());
        updatedUser.setCreatedBy(currentUser.getCreatedBy());
        updatedUser.setUpdatedBy(updatedBy);

        userRepository.save(updatedUser);
    }

    /**
     * Generates a password reset token and stores it on the current user record.
     * Token is a random UUID with a 1 hour expiry.
     *
     * In production this token would be sent to the user's email address
     * via the notification-service. It is returned directly here for
     * testing and development purposes only — never expose reset tokens
     * in a production API response.
     *
     * @param email the email address of the user requesting the reset
     * @return the generated reset token string
     * @throws RuntimeException if no active account found for the email
     */
    public String requestPasswordReset(String email) {
        // Find current active user
        User currentUser = userRepository.findCurrentByEmail(email)
                .orElseThrow(() -> new RuntimeException("No active account found for email: " + email));

        // Generate a secure random token
        String resetToken = java.util.UUID.randomUUID().toString();

        // Set token and expiry (1 hour from now)
        currentUser.setResetToken(resetToken);
        currentUser.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        currentUser.setUpdatedBy("system");
        userRepository.save(currentUser);

        // In production this token would be emailed via notification-service
        // For now we return it directly for testing purposes
        return resetToken;
    }

    /**
     * Validates a password reset token and updates the user's password.
     * Checks the token exists, has not expired (1 hour validity) and
     * that the new password has not been previously used.
     *
     * Creates a new effective dated version with the updated password —
     * reset token fields are cleared on the new version.
     * The old version is closed with endDate set to the reset timestamp.
     *
     * @param resetToken the UUID reset token from the reset request
     * @param newPassword the new plain text password to hash and store
     * @throws RuntimeException if token invalid, expired or password previously used
     */
    public void confirmPasswordReset(String resetToken, String newPassword) {
        // Find user by reset token
        User currentUser = userRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        // Check token has not expired
        if (currentUser.getResetTokenExpiry() == null ||
                LocalDateTime.now().isAfter(currentUser.getResetTokenExpiry())) {
            throw new RuntimeException("Reset token has expired - please request a new one");
        }

        // Create new version with updated password
        LocalDateTime now = LocalDateTime.now();

        // Ensure new password not previously used
        validatePasswordNotPreviouslyUsed(
                currentUser.getUserId().getId(), newPassword);

        // Close current record
        currentUser.setEndDate(now);
        currentUser.setUpdatedBy("system");
        userRepository.save(currentUser);

        // Create new version
        UserId newUserId = new UserId();
        newUserId.setId(currentUser.getUserId().getId());
        newUserId.setEffectiveDate(now.plusNanos(1000));

        User updatedUser = new User();
        updatedUser.setUserId(newUserId);
        updatedUser.setTitle(currentUser.getTitle());
        updatedUser.setFirstName(currentUser.getFirstName());
        updatedUser.setMiddleName(currentUser.getMiddleName());
        updatedUser.setLastName(currentUser.getLastName());
        updatedUser.setDateOfBirth(currentUser.getDateOfBirth());
        updatedUser.setEmail(currentUser.getEmail());
        updatedUser.setPhoneNumber(currentUser.getPhoneNumber());
        updatedUser.setPassword(passwordEncoder.encode(newPassword));
        updatedUser.setEmailVerified(currentUser.isEmailVerified());
        updatedUser.setStatus(currentUser.getStatus());
        updatedUser.setRole(currentUser.getRole());
        updatedUser.setCreatedBy(currentUser.getCreatedBy());
        updatedUser.setUpdatedBy("system");
        // Clear reset token on new version
        updatedUser.setResetToken(null);
        updatedUser.setResetTokenExpiry(null);

        userRepository.save(updatedUser);
    }

    /**
     * Validates that a new password has not been used in any previous version
     * of the user's account. Uses BCrypt matching against all historical
     * password hashes retrieved from the effective dating audit trail.
     *
     * This method deliberately leverages the existing version history rather
     * than maintaining a separate password history table — the effective
     * dating model provides this capability as a side effect.
     *
     * @param id the user's generated sequence ID
     * @param newPassword the plain text password to validate
     * @throws RuntimeException if the password has been previously used
     */
    private void validatePasswordNotPreviouslyUsed(Long id, String newPassword) {
        List<String> previousPasswords = userRepository.findAllPasswordsById(id);
        boolean previouslyUsed = previousPasswords.stream()
                .anyMatch(oldHash -> passwordEncoder.matches(newPassword, oldHash));
        if (previouslyUsed) {
            throw new RuntimeException(
                    "New password cannot be the same as any previously used password");
        }
    }
}