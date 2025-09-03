package ru.auth.controller;



import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import ru.auth.model.AuthenticationRequest;
import ru.auth.model.AuthenticationResponse;
import ru.auth.service.JwtService;
import ru.auth.service.UserService;
import ru.auth.model.RefreshRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ru.auth.service.RefreshTokenService;
import org.springframework.web.bind.annotation.CookieValue;
import java.time.Duration;
import java.time.Instant;

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
	public ResponseEntity<?> createAuthenticationToken(@Valid @RequestBody AuthenticationRequest authenticationRequest, HttpServletRequest request) {
		try {
			final String jwt = jwtService.createJwtToken(
					authenticationRequest.getUsername(),
					authenticationRequest.getPassword()
			);
			var user = userService.findByUsername(authenticationRequest.getUsername()).orElseThrow();
			var refresh = refreshTokenService.issue(user);
			ResponseCookie cookie = buildRefreshCookie(refresh.getToken(), refresh.getExpiresAt(), request);
			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, cookie.toString())
					.body(new AuthenticationResponse(jwt));
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
	@Operation(summary = "Выход", description = "Клиент чистит локальные токены; если передан refresh, он будет отозван")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Успех: независимо от состояния refresh")
	})
	public ResponseEntity<?> logout(@Valid @RequestBody(required = false) RefreshRequest body, @CookieValue(name = "refresh_token", required = false) String cookieToken, HttpServletRequest request) {
		try {
			String incoming = cookieToken != null && !cookieToken.isBlank() ? cookieToken : (body != null ? body.getRefreshToken() : null);
			if (incoming != null && !incoming.isBlank()) {
				var token = refreshTokenService.validateActive(incoming);
				refreshTokenService.revoke(token);
			}
		} catch (Exception ignored) {
		}

		ResponseCookie delete = ResponseCookie.from("refresh_token", "")
				.path("/auth")
				.maxAge(Duration.ZERO)
				.httpOnly(true)
				.sameSite(resolveSameSite(request))
				.secure(resolveSecure(request))
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, delete.toString())
				.body(java.util.Map.of("ok", true));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Обновить JWT и refresh (ротация)", description = "По валидному refresh выполняется ротация: старый revocation, выдаётся новый refresh и новый JWT")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Успех: возвращены новый JWT и новый refresh"),
			@ApiResponse(responseCode = "401", description = "Недействительный или повторно использованный refresh")
	})
	public ResponseEntity<?> refresh(@Valid @RequestBody(required = false) RefreshRequest body, @CookieValue(name = "refresh_token", required = false) String cookieToken, HttpServletRequest request) {
		String refreshToken = null;
		if (cookieToken != null && !cookieToken.isBlank()) {
			refreshToken = cookieToken;
		} else if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
			refreshToken = body.getRefreshToken();
		}
		if (refreshToken == null || refreshToken.isBlank()) {
			return ResponseEntity.status(401).body("Unauthorized: refresh token required");
		}
		try {
			var newToken = refreshTokenService.rotateByValue(refreshToken);
			var user = newToken.getUser();
			final String jwt = jwtService.generateJwtForUsername(user.getUsername());
			ResponseCookie cookie = buildRefreshCookie(newToken.getToken(), newToken.getExpiresAt(), request);
			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, cookie.toString())
					.body(new AuthenticationResponse(jwt));
		} catch (Exception ex) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}
	}

	private ResponseCookie buildRefreshCookie(String token, Instant expiresAt, HttpServletRequest request) {
		Duration maxAge = Duration.between(Instant.now(), expiresAt);
		if (maxAge.isNegative()) maxAge = Duration.ZERO;
		return ResponseCookie.from("refresh_token", token)
				.path("/auth")
				.maxAge(maxAge)
				.httpOnly(true)
				.sameSite(resolveSameSite(request))
				.secure(resolveSecure(request))
				.build();
	}

	private boolean resolveSecure(HttpServletRequest request) {
		String proto = request.getHeader("X-Forwarded-Proto");
		if (proto != null) return "https".equalsIgnoreCase(proto);
		return request.isSecure();
	}

	private String resolveSameSite(HttpServletRequest request) {
		boolean secure = resolveSecure(request);
		return secure ? "None" : "Lax";
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
