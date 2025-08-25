package ru.auth.controller;



import lombok.RequiredArgsConstructor;
import ru.auth.model.AuthenticationRequest;
import ru.auth.model.AuthenticationResponse;
import ru.auth.model.User;
import ru.auth.service.JwtService;
import ru.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Аутентификация и управление пользователями")
public class AuthController {

	private final JwtService jwtService;

	private final UserService userService;

	@PostMapping("/login")
	@Operation(summary = "Логин", description = "Аутентификация пользователя и выдача JWT")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Успех"),
		@ApiResponse(responseCode = "401", description = "Неверные данные")
	})
	public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthenticationRequest authenticationRequest) {
		try {
			final String jwt = jwtService.createJwtToken(
					authenticationRequest.getUsername(),
					authenticationRequest.getPassword()
			);
			return ResponseEntity.ok(new AuthenticationResponse(jwt));
		} catch (Exception ex) {
			return ResponseEntity.status(401).body("Invalid username or password.");
		}
	}

	@PostMapping("/signup")
	@Operation(summary = "Регистрация", description = "Создание нового пользователя")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Пользователь зарегистрирован"),
		@ApiResponse(responseCode = "400", description = "Имя пользователя занято")
	})
	public ResponseEntity<?> registerUser(@RequestBody User user) {
		if (userService.findByUsername(user.getUsername()).isPresent()) {
			return ResponseEntity.badRequest().body("Username is already taken.");
		}
		userService.save(user);
		return ResponseEntity.ok("User registered successfully.");
	}

	@GetMapping("/me")
	@Operation(summary = "Текущий пользователь", description = "Информация о текущем пользователе",
		security = { @SecurityRequirement(name = "bearerAuth") })
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Успех"),
		@ApiResponse(responseCode = "401", description = "Неавторизован")
	})
	public ResponseEntity<?> me(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		var principal = authentication.getName();
		var roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return ResponseEntity.ok(java.util.Map.of(
				"username", principal,
				"roles", roles
		));
	}
}

