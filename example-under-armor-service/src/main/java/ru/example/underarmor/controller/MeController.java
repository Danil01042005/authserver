package ru.example.underarmor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/example-under-armor")
@Tag(name = "ExampleUnderArmor", description = "Пример защищённого микросервиса")
public class MeController {

	@GetMapping("/me")
	@Operation(summary = "Текущий пользователь", description = "Возвращает имя и роли из JWT")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Успех"),
		@ApiResponse(responseCode = "401", description = "Неавторизован")
	})
	public ResponseEntity<?> me(Authentication authentication) {
		var principal = authentication.getName();
		var roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return ResponseEntity.ok(Map.of("username", principal, "roles", roles));
	}
}



