package com.test.financialtracker.identity.service;


import com.test.financialtracker.common.exception.ConflictException;
import com.test.financialtracker.identity.domain.models.AuthResponse;
import com.test.financialtracker.identity.domain.models.LoginRequest;
import com.test.financialtracker.identity.domain.models.RegisterRequest;
import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.domain.transformers.UserMapper;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import com.test.financialtracker.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final IdentityProviderPort identityProvider;


    @Transactional
    public AuthResponse register(RegisterRequest request) {
//TODO:Decide fail or return response bc what if net exception and the user registered
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration attempt for existing email={}", request.email());
            throw new ConflictException("Email already registered");
        }

        String keycloakId = identityProvider.registerUser(
                request.email(),
                request.password(),
                User.Role.USER
        );

        User newUser = User.newUser(keycloakId, request.email());
        userRepository.save(userMapper.toEntity(newUser));

        log.info("User registered successfully userId={} email={}", newUser.getId(), request.email());
        return AuthResponse.registered(newUser.getId(), newUser.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        IdentityProviderPort.TokenResponse tokenResponse =
                identityProvider.authenticate(request.email(), request.password());


        String keycloakId = extractSubFromJwt(tokenResponse.accessToken());

        User user = userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toDomain)
                .orElseThrow(() -> {

                    log.error("Local user not found for keycloakId={} — identity sync issue", keycloakId);
                    return new IllegalStateException("User profile not found. Contact support.");
                });

        log.info("User logged in userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());

        return AuthResponse.authenticated(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.expiresIn()
        );
    }


    /**
     * We do NOT verify the signature here — Spring Security's JwtDecoder
     * already verified it on the request filter chain before we got here.
     * This is just payload extraction from the already-trusted token.
     */
    private String extractSubFromJwt(String jwt) {
        try {
            String payload = jwt.split("\\.")[1];
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
            String json = new String(decoded);
            int start = json.indexOf("\"sub\":\"") + 7;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract sub from JWT", e);
        }
    }
}
