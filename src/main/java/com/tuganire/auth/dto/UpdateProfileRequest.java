package com.tuganire.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "{validation.firstName.notblank}")
    @Size(max = 100, message = "{validation.firstName.size}")
    private String firstName;

    @NotBlank(message = "{validation.lastName.notblank}")
    @Size(max = 100, message = "{validation.lastName.size}")
    private String lastName;
}
