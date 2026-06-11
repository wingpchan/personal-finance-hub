package com.financehub.user_service.entity;

import jakarta.persistence.Embeddable;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Embeddable
public class UserId implements Serializable {

    private Long id;
    private LocalDateTime effectiveDate;
}