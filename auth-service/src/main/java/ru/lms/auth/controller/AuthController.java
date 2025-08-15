package ru.lms.auth.controller;



import lombok.RequiredArgsConstructor;
import ru.lms.auth.model.AuthenticationRequest;
import ru.lms.auth.model.AuthenticationResponse;
import ru.lms.auth.model.User;
import ru.lms.auth.service.JwtService;
import ru.lms.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    private final UserService userService;

    @PostMapping("/login")
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
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (userService.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username is already taken.");
        }
        userService.save(user);
        return ResponseEntity.ok("User registered successfully.");
    }

    @GetMapping("/me")
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