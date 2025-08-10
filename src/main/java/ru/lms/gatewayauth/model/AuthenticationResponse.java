package ru.lms.gatewayauth.model;

import lombok.Value;

@Value
public class AuthenticationResponse {
    String jwt;
}