package com.campus.complaints;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static com.campus.complaints.AuthRequests.LoginRequest;
import static com.campus.complaints.AuthRequests.RegisterRequest;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        String rollNumber = request.rollNumber().trim();
        if (userRepository.existsByRollNumber(rollNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this roll number already exists.");
        }

        User user = new User(request.name().trim(), email, UserRole.STUDENT, findDepartment(request.departmentId()));
        user.setStudentDetails(
                rollNumber,
                request.className() == null ? null : request.className().trim(),
                request.section() == null ? null : request.section().trim());
        user.setPasswordHash(encodePassword(request.password()));
        user = userRepository.save(user);
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
        boolean validPasswordLength = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        String hashToCheck = user == null || user.getPasswordHash() == null
                ? dummyPasswordHash
                : user.getPasswordHash();
        String passwordToCheck = validPasswordLength ? request.password() : "InvalidPasswordCheck123";
        boolean passwordMatches = passwordEncoder.matches(passwordToCheck, hashToCheck);
        if (!validPasswordLength || user == null || user.getPasswordHash() == null || !passwordMatches) {
            String rejectionCategory = !validPasswordLength
                    ? "PASSWORD_EXCEEDS_BCRYPT_LIMIT"
                    : user == null || !user.isActive() || user.getPasswordHash() == null
                            ? "ACCOUNT_UNAVAILABLE"
                            : "PASSWORD_MISMATCH";
            logger.warn("Login rejected: {}.", rejectionCategory);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        logger.debug("Login succeeded for role {}.", user.getRole());
        return createAuthResponse(user);
    }

    @Transactional
    public UserProfileResponse provision(AuthRequests.ProvisionUserRequest request, AuthenticatedUser actor) {
        if (request.role() != UserRole.FACULTY && request.role() != UserRole.STAFF) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Administrators may provision Faculty or Staff accounts only.");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        User user = new User(request.name().trim(), email, request.role(), findDepartment(request.departmentId()));
        user.setPasswordHash(encodePassword(request.password()));
        user = userRepository.saveAndFlush(user);
        auditService.record(actor, "ACCOUNT_CREATED", "USER", user.getId().toString(), user.getEmail(),
                null, null, "Created " + user.getRole() + " account " + user.getEmail() + ".",
                null, Map.of("name", user.getFullName(), "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "departmentId", user.getDepartment() == null ? "" : user.getDepartment().getId()));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse getProfile(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
        return UserProfileResponse.from(user);
    }

    private AuthResponse createAuthResponse(User user) {
        return new AuthResponse(jwtService.createAccessToken(user), "Bearer", UserProfileResponse.from(user));
    }

    private Department findDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department was not found."));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String encodePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Password must be no longer than 72 UTF-8 bytes.");
        }
        return passwordEncoder.encode(password);
    }
}
