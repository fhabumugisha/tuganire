package com.tuganire.auth.dto;

import com.tuganire.shared.constant.PlanType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private PlanType plan;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /**
     * Returns the user's full name (firstName + lastName).
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(lastName.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Returns the user's initials (first letter of firstName + first letter of lastName).
     */
    public String getInitials() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(Character.toUpperCase(firstName.trim().charAt(0)));
        }
        if (lastName != null && !lastName.isBlank()) {
            sb.append(Character.toUpperCase(lastName.trim().charAt(0)));
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
