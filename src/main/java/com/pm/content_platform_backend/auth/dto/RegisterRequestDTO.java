package com.pm.content_platform_backend.auth.dto;



import com.pm.content_platform_backend.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class RegisterRequestDTO {
    @NotBlank(message = "Username cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    @Length(min = 10, max = 30, message = "Username must be between 10 and 50 characters")
    private String username;
    @NotBlank(message = "Password cannot be blank")
    @StrongPassword
    private String password;
}
