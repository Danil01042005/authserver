package ru.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken must not be blank")
    private String refreshToken;
}





