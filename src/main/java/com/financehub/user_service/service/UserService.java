package com.financehub.user_service.service;

import com.financehub.user_service.dto.LoginRequest;
import com.financehub.user_service.dto.LoginResponse;
import com.financehub.user_service.enums.Title;
import com.financehub.user_service.enums.UserStatus;
import com.financehub.user_service.enums.Role;
import com.financehub.user_service.entity.User;
import com.financehub.user_service.entity.UserId;
import com.financehub.user_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
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

    // Admin only - create an admin user
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

    // Register a new user
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

    // Update user - creates a new version
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

    // Soft delete
    public void deleteUser(Long id, String deletedBy) {
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        currentUser.setEndDate(LocalDateTime.now());
        currentUser.setUpdatedBy(deletedBy);
        userRepository.save(currentUser);
    }

    // Admin only - reinstate a deleted user
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
    public User updateStatus(Long id, UserStatus status, String updatedBy) {
        User currentUser = userRepository.findCurrentById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        currentUser.setStatus(status);
        currentUser.setUpdatedBy(updatedBy);
        return userRepository.save(currentUser);
    }

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

    // Request password reset - generates reset token
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

    // Confirm password reset - validates token and updates password
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

    // Check new password has not been used before
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