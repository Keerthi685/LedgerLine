
package com.ledgerline.controller;

import com.ledgerline.entity.AppUser;
import com.ledgerline.entity.Role;
import com.ledgerline.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Username already exists"));
        }

        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Email already exists"));
        }

        AppUser user = new AppUser();

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(
            passwordEncoder.encode(request.password())
        );
        user.setRole(Role.ACCOUNT_HOLDER);

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "message", "Registration successful",
                "username", user.getUsername(),
                "role", user.getRole().name()
            ));
    }
}
