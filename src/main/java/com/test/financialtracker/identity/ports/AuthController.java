package com.test.financialtracker.identity.ports;

import com.test.financialtracker.identity.domain.models.AuthResponse;
import com.test.financialtracker.identity.domain.models.LoginRequest;
import com.test.financialtracker.identity.domain.models.RegisterRequest;
import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.service.AuthService;
import com.test.financialtracker.utils.SecurityContextHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextHelper securityContextHelper;


    /**
     * POST /api/v1/auth/register
     * <p>
     * Happy path:    201 Created  + AuthResponse (no token)
     * Duplicate:     409 Conflict (thrown by AuthService → handled by GlobalExceptionHandler)
     * Invalid body:  400 Bad Request (thrown by @Valid → MethodArgumentNotValidException)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request, User.Role.USER);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/admin/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> registerAdminUser(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request, User.Role.ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * <p>
     * Happy path:         200 OK  + AuthResponse (includes JWT)
     * Wrong credentials:  401 Unauthorized (IdentityProviderException from KeycloakAdapter)
     * Invalid body:       400 Bad Request
     * <p>
     * Rate limiting is applied at the filter level (RateLimitingFilter)
     * before this controller is ever reached.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<AuthResponse> logout() {
        AuthResponse response = authService.logout(securityContextHelper.getAuthenticatedUserId());
        return ResponseEntity.ok(response);
    }

}
