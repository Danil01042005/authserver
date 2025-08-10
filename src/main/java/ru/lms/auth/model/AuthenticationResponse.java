package ru.lms.auth.model;

import lombok.Value;

@Value
public class AuthenticationResponse {
    String jwt;
}