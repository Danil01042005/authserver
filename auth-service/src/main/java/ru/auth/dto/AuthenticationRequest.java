package ru.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthenticationRequest {

    @NotBlank(message = "username must not be blank")
    @Size(min = 3, max = 64, message = "username length must be 3 - 64")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(min = 6, max = 128, message = "password length must be 6 - 128")
    private String password;

}





