package com.financehub.user_service.controller;

import com.financehub.user_service.dto.*;
import com.financehub.user_service.service.UserService;
import com.financehub.user_service.enums.UserStatus;
import com.financehub.user_service.entity.User;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }

    // Admin only - register an admin user
    @PostMapping("/register/admin")
    public ResponseEntity<User> registerAdmin(
            @Valid @RequestBody User user,
            @RequestHeader(value = "X-Created-By", defaultValue = "system") String createdBy) {
        User savedUser = userService.registerAdmin(user, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    // Register new user
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(
            @Valid @RequestBody User user,
            @RequestHeader(value = "X-Created-By", defaultValue = "system") String createdBy) {
        User savedUser = userService.registerUser(user, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    // Get current user by ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getCurrentUser(@PathVariable Long id) {
        return userService.findCurrentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get full version history for a user
    @GetMapping("/{id}/history")
    public ResponseEntity<List<User>> getUserHistory(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findAllVersionsById(id));
    }

    // Get user at a specific point in time
    @GetMapping("/{id}/at")
    public ResponseEntity<List<User>> getUserAtPointInTime(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime queryDate) {
        return ResponseEntity.ok(userService.findAtPointInTime(id, queryDate));
    }

    // Get current user by email
    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userService.findCurrentByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all users
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(userService.findAll(activeOnly));
    }

    // Search users
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly) {

        // Reject ambiguous combination
        if (name != null && (firstName != null || lastName != null)) {
            return ResponseEntity.badRequest()
                    .body("Cannot combine 'name' with 'firstName' or 'lastName' parameters");
        }

        if (firstName != null && lastName != null) {
            return ResponseEntity.ok(
                    userService.searchByFirstAndLastName(firstName, lastName, activeOnly));
        }
        if (name != null) {
            return ResponseEntity.ok(userService.searchByName(name, activeOnly));
        }
        if (firstName != null) {
            return ResponseEntity.ok(userService.searchByFirstName(firstName, activeOnly));
        }
        if (lastName != null) {
            return ResponseEntity.ok(userService.searchByLastName(lastName, activeOnly));
        }
        return ResponseEntity.ok(userService.findAll(activeOnly));
    }

    // Update user - creates new version
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody User user,
            @RequestHeader(value = "X-Updated-By", defaultValue = "system") String updatedBy) {
        return ResponseEntity.ok(userService.updateUser(id, user, updatedBy));
    }

    // Soft delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-Deleted-By", defaultValue = "system") String deletedBy) {
        userService.deleteUser(id, deletedBy);
        return ResponseEntity.noContent().build();
    }

    // Admin only - reinstate a deleted user
    @PatchMapping("/{id}/reinstate")
    public ResponseEntity<User> reinstateUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-Updated-By", defaultValue = "system") String updatedBy) {
        return ResponseEntity.ok(userService.reinstateUser(id, updatedBy));
    }

    // Verify email
    @PatchMapping("/{id}/verify-email")
    public ResponseEntity<User> verifyEmail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Updated-By", defaultValue = "system") String updatedBy) {
        return ResponseEntity.ok(userService.verifyEmail(id, updatedBy));
    }

    // Update status
    @PatchMapping("/{id}/status")
    public ResponseEntity<User> updateStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status,
            @RequestHeader(value = "X-Updated-By", defaultValue = "system") String updatedBy) {
        return ResponseEntity.ok(userService.updateStatus(id, status, updatedBy));
    }

    // Change password
    @PatchMapping("/{id}/change-password")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "X-Updated-By", defaultValue = "system") String updatedBy) {
        userService.changePassword(
                id,
                request.getCurrentPassword(),
                request.getNewPassword(),
                updatedBy);
        return ResponseEntity.noContent().build();
    }

    // Request password reset
    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        String resetToken = userService.requestPasswordReset(request.getEmail());

        // In production the token would be emailed, not returned here
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset token generated successfully");
        response.put("resetToken", resetToken);
        response.put("note", "In production this token would be sent via email");
        return ResponseEntity.ok(response);
    }

    // Confirm password reset
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.confirmPasswordReset(
                request.getResetToken(),
                request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}