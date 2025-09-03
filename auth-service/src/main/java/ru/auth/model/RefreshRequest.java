package ru.auth.model;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken must not be blank")
    private String refreshToken;
}

