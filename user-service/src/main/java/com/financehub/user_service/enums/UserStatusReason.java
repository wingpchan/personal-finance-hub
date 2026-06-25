package com.financehub.user_service.enums;
/**
 * Defined reasons for a change in UserStatus, supporting audit and
 * regulatory traceability. Captured alongside each effective-dated
 * version when status changes.
 */
public enum UserStatusReason {

    // Customer initiated
    CUSTOMER_REQUEST,

    // Verification / onboarding
    DOCUMENT_VERIFICATION_FAILED,
    EMAIL_VERIFICATION_EXPIRED,

    // Risk / compliance
    FRAUD_INVESTIGATION,
    SECURITY_CONCERN,
    COMPLIANCE_REVIEW,
    REGULATORY_ACTION,
    SANCTIONED,

    // Lifecycle
    DORMANT_ACCOUNT,
    DUPLICATE_ACCOUNT,
    DECEASED,

    // Administrative
    ADMIN_CORRECTION,
    INVESTIGATION_CLOSED,
    SYSTEM_AUTOMATED,

    OTHER
}
