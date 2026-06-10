package com.financehub.user_service;

public enum UserStatus {
    PENDING_VERIFICATION,   // Registered but email not yet confirmed
    ACTIVE,                 // Fully verified and active
    SUSPENDED,              // Temporarily blocked e.g. failed logins
    INACTIVE,               // Voluntarily deactivated by user
    CLOSED                  // Account permanently closed
}