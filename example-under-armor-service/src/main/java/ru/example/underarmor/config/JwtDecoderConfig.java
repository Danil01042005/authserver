package ru.example.underarmor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri
    ) {
        RestTemplate tracingRestTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
                    factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
                    return factory;
                })
                .build();

        return NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .restOperations(tracingRestTemplate)
                .build();
    }
}


