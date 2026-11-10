package com.ledgerline.config;

import com.ledgerline.entity.AppUser;
import com.ledgerline.entity.Role;
import com.ledgerline.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    @Bean
    CommandLineRunner createBootstrapAdmin(
            UserRepository users, PasswordEncoder encoder,
            @Value("${LEDGERLINE_ADMIN_USERNAME:}") String username,
            @Value("${LEDGERLINE_ADMIN_EMAIL:}") String email,
            @Value("${LEDGERLINE_ADMIN_PASSWORD:}") String password) {
        return args -> {
            if (username.isBlank() && email.isBlank() && password.isBlank()) return;
            if (username.isBlank() || email.isBlank() || password.length() < 12) {
                throw new IllegalStateException("Set LEDGERLINE_ADMIN_USERNAME, LEDGERLINE_ADMIN_EMAIL and a password of at least 12 characters together");
            }
            if (!users.existsByUsername(username) && !users.existsByEmail(email)) {
                AppUser admin = new AppUser();
                admin.setUsername(username);
                admin.setEmail(email);
                admin.setPasswordHash(encoder.encode(password));
                admin.setRole(Role.ADMIN);
                users.save(admin);
            }
        };
    }
}
