package com.financehub.user_service;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
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
        if (!updatedUser.getEmail().equals(currentUser.getEmail())
                && userRepository.existsByEmail(updatedUser.getEmail())) {
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
}