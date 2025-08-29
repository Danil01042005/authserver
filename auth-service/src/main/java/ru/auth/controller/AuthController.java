package ru.auth.controller;



import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import ru.auth.model.AuthenticationRequest;
import ru.auth.model.AuthenticationResponse;
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
import ru.auth.service.RefreshTokenService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Аутентификация и управление пользователями")
public class AuthController {

	private final JwtService jwtService;

	private final UserService userService;

	private final RefreshTokenService refreshTokenService;

	@PostMapping("/login")
	@Operation(summary = "Логин", description = "Аутентификация пользователя и выдача JWT")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Успех"),
		@ApiResponse(responseCode = "401", description = "Неверные данные")
	})
	public ResponseEntity<?> createAuthenticationToken(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
		try {
			final String jwt = jwtService.createJwtToken(
					authenticationRequest.getUsername(),
					authenticationRequest.getPassword()
			);
			var user = userService.findByUsername(authenticationRequest.getUsername()).orElseThrow();
			var refresh = refreshTokenService.issue(user);
			return ResponseEntity.ok(new AuthenticationResponse(jwt, refresh.getToken()));
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
	public ResponseEntity<?> registerUser(@Valid @RequestBody ru.auth.model.SignupRequest req) {
		if (userService.findByUsername(req.getUsername()).isPresent()) {
			return ResponseEntity.badRequest().body("Username is already taken.");
		}
		ru.auth.model.User u = new ru.auth.model.User();
		u.setUsername(req.getUsername());
		u.setPassword(req.getPassword());
		userService.save(u);
		return ResponseEntity.ok("User registered successfully.");
	}

	@PostMapping("/logout")
	@Operation(summary = "Выход", description = "Отозвать текущий refresh-токен")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Успех"),
			@ApiResponse(responseCode = "400", description = "Нет токена"),
			@ApiResponse(responseCode = "401", description = "Недействительный refresh")
	})
	@SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<?> logout(@RequestBody java.util.Map<String, String> body) {
		String refreshToken = body.get("refreshToken");
		if (refreshToken == null || refreshToken.isBlank()) {
			return ResponseEntity.status(400).body("refreshToken is required");
		}
		try {
			var token = refreshTokenService.validateActive(refreshToken);
			refreshTokenService.revoke(token);
			return ResponseEntity.ok(java.util.Map.of("revoked", true));
		} catch (Exception ex) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}
	}

	@PostMapping("/refresh")
	@Operation(summary = "Обновить JWT", description = "Получить новый JWT по refresh-токену")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Успех"),
			@ApiResponse(responseCode = "401", description = "Недействительный refresh")
	})
	public ResponseEntity<?> refresh(@RequestBody java.util.Map<String, String> body) {
		String refreshToken = body.get("refreshToken");
		if (refreshToken == null || refreshToken.isBlank()) {
			return ResponseEntity.status(400).body("refreshToken is required");
		}
		try {
			var token = refreshTokenService.validateActive(refreshToken);
			var user = token.getUser();
			final String jwt = jwtService.generateJwtForUsername(user.getUsername());
			return ResponseEntity.ok(new AuthenticationResponse(jwt, token.getToken()));
		} catch (Exception ex) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}
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

