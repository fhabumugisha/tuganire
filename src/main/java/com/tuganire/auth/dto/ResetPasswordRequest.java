package com.tuganire.auth.dto;

import com.tuganire.auth.validation.PasswordMatches;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@PasswordMatches
public class ResetPasswordRequest {

    @NotBlank(message = "{validation.token.notblank}")
    private String token;

    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 8, max = 100, message = "{validation.password.size}")
    private String password;

    @NotBlank(message = "{validation.password.confirm.notblank}")
    private String confirmPassword;
}
