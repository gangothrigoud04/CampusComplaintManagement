package com.campus.complaints;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${BOOTSTRAP_ADMIN_NAME}") String name,
            @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            throw new IllegalStateException("Admin bootstrap refused because an administrator already exists.");
        }
        if (name.isBlank() || email.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new IllegalStateException("Bootstrap administrator values are missing or do not meet password rules.");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Bootstrap administrator email is already in use.");
        }

        User administrator = new User(name.trim(), normalizedEmail, UserRole.ADMIN, null);
        administrator.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(administrator);
    }
}
