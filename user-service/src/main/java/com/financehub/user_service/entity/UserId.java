package com.financehub.user_service.entity;

import jakarta.persistence.Embeddable;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Composite primary key for the User entity combining a generated Long ID
 * and an effectiveDate timestamp.
 *
 * The combination of id and effectiveDate uniquely identifies each version
 * of a user record. Multiple rows can share the same id — each representing
 * a different point in time version of that user.
 *
 * Must implement Serializable as required by the JPA specification
 * for all composite key classes.
 *
 * @see User
 */
@Data
@Embeddable
public class UserId implements Serializable {

    private Long id;
    private LocalDateTime effectiveDate;
}