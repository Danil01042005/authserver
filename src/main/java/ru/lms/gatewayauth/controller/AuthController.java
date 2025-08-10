package ru.lms.gatewayauth.controller;



import lombok.RequiredArgsConstructor;
import ru.lms.gatewayauth.model.AuthenticationRequest;
import ru.lms.gatewayauth.model.AuthenticationResponse;
import ru.lms.gatewayauth.model.User;
import ru.lms.gatewayauth.service.JwtService;
import ru.lms.gatewayauth.service.UserService;
import org.springframework.http.ResponseEntity;
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
}