package ru.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.auth.config.RsaKeyProvider;

@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return rsaKeyProvider.getJwksJson();
    }
}





