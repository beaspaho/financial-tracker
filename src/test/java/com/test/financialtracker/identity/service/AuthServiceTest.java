package com.test.financialtracker.identity.service;

import com.test.financialtracker.common.exception.ConflictException;
import com.test.financialtracker.common.exception.IdentityProviderException;
import com.test.financialtracker.common.exception.UserIdentityUnknownException;
import com.test.financialtracker.identity.domain.entity.Users;
import com.test.financialtracker.identity.domain.models.AuthResponse;
import com.test.financialtracker.identity.domain.models.LoginRequest;
import com.test.financialtracker.identity.domain.models.RegisterRequest;
import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.domain.transformers.UserMapper;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import com.test.financialtracker.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    IdentityProviderPort identityProvider;

    // Real mapper — no external deps, mapping logic is part of what we verify
    final UserMapper userMapper = new UserMapper();

    DefaultAuthService service;

    UUID userId = UUID.randomUUID();
    String keycloakId = UUID.randomUUID().toString();
    String email = "user@example.com";

    Users savedEntity;

    @BeforeEach
    void setUp() {
        service = new DefaultAuthService(userRepository, userMapper, identityProvider);

        savedEntity = Users.builder()
                .id(userId)
                .keycloakId(keycloakId)
                .email(email)
                .role(User.Role.USER)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // REGISTER
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class Register {

        RegisterRequest req = new RegisterRequest(email, "Password1!", "Alice", "Smith");

        @Test
        @DisplayName("Happy path — user created in Keycloak and persisted locally")
        void register_success() {
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(identityProvider.registerUser(any())).thenReturn(keycloakId);
            when(userRepository.save(any())).thenReturn(savedEntity);

            AuthResponse response = service.register(req, User.Role.USER);

            assertThat(response.email()).isEqualTo(email);
            assertThat(response.message()).contains("Registration successful");
            assertThat(response.accessToken()).isNull();   // registration does not issue a token
            assertThat(response.userId()).isNotNull();
        }

        @Test
        @DisplayName("Keycloak registration request carries the correct user details")
        void register_keycloakRequestHasCorrectFields() {
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(identityProvider.registerUser(any())).thenReturn(keycloakId);
            when(userRepository.save(any())).thenReturn(savedEntity);

            ArgumentCaptor<IdentityProviderPort.UserRegistrationRequest> captor =
                    ArgumentCaptor.forClass(IdentityProviderPort.UserRegistrationRequest.class);

            service.register(req, User.Role.USER);

            verify(identityProvider).registerUser(captor.capture());
            IdentityProviderPort.UserRegistrationRequest sent = captor.getValue();

            assertThat(sent.email()).isEqualTo(email);
            assertThat(sent.firstName()).isEqualTo("Alice");
            assertThat(sent.lastName()).isEqualTo("Smith");
            assertThat(sent.role()).isEqualTo(User.Role.USER);
            assertThat(sent.appUserId()).isNotNull(); // a fresh UUID is generated
        }

        @Test
        @DisplayName("Admin role — registration request carries ADMIN role to Keycloak")
        void register_adminRole_sentToKeycloak() {
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(identityProvider.registerUser(any())).thenReturn(keycloakId);
            when(userRepository.save(any())).thenReturn(savedEntity);

            ArgumentCaptor<IdentityProviderPort.UserRegistrationRequest> captor =
                    ArgumentCaptor.forClass(IdentityProviderPort.UserRegistrationRequest.class);

            service.register(req, User.Role.ADMIN);

            verify(identityProvider).registerUser(captor.capture());
            assertThat(captor.getValue().role()).isEqualTo(User.Role.ADMIN);
        }

        @Test
        @DisplayName("Email already registered — throws ConflictException without touching Keycloak")
        void register_emailTaken_throwsConflict() {
            when(userRepository.existsByEmail(email)).thenReturn(true);

            assertThatThrownBy(() -> service.register(req, User.Role.USER))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already registered");

            verifyNoInteractions(identityProvider);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Keycloak failure — IdentityProviderException propagates, local user not saved")
        void register_keycloakFailure_propagatesAndRollsBack() {
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(identityProvider.registerUser(any()))
                    .thenThrow(new IdentityProviderException("Keycloak unavailable", 503));

            assertThatThrownBy(() -> service.register(req, User.Role.USER))
                    .isInstanceOf(IdentityProviderException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LOGIN
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("login()")
    class Login {

        LoginRequest req = new LoginRequest(email, "Password1!");

        @Test
        @DisplayName("Happy path — returns access/refresh tokens and user details")
        void login_success() {
            String fakeJwt = buildJwt(keycloakId);
            IdentityProviderPort.TokenResponse token =
                    new IdentityProviderPort.TokenResponse(fakeJwt, "refresh-tok", 3600, "Bearer");

            when(identityProvider.authenticate(email, "Password1!")).thenReturn(token);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(savedEntity));

            AuthResponse response = service.login(req);

            assertThat(response.email()).isEqualTo(email);
            assertThat(response.accessToken()).isEqualTo(fakeJwt);
            assertThat(response.refreshToken()).isEqualTo("refresh-tok");
            assertThat(response.expiresIn()).isEqualTo(3600);
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.message()).isNull(); // no message on successful login
        }

        @Test
        @DisplayName("keycloakId extracted from JWT 'sub' claim matches what is used to look up local user")
        void login_subExtractedFromJwt_usedForLocalLookup() {
            String fakeJwt = buildJwt(keycloakId);
            IdentityProviderPort.TokenResponse token =
                    new IdentityProviderPort.TokenResponse(fakeJwt, "ref", 3600, "Bearer");

            when(identityProvider.authenticate(email, "Password1!")).thenReturn(token);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(savedEntity));

            service.login(req);

            verify(userRepository).findByKeycloakId(keycloakId);
        }

        @Test
        @DisplayName("Keycloak auth succeeds but local user record missing — throws IllegalStateException")
        void login_localUserNotFound_throwsIllegalState() {
            String fakeJwt = buildJwt(keycloakId);
            IdentityProviderPort.TokenResponse token =
                    new IdentityProviderPort.TokenResponse(fakeJwt, "ref", 3600, "Bearer");

            when(identityProvider.authenticate(email, "Password1!")).thenReturn(token);
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User profile not found");
        }

        @Test
        @DisplayName("Wrong credentials — IdentityProviderException propagates")
        void login_wrongCredentials_propagatesKeycloakError() {
            when(identityProvider.authenticate(email, "Password1!"))
                    .thenThrow(new IdentityProviderException("Invalid credentials", 401));

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IdentityProviderException.class);

            verifyNoInteractions(userRepository);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LOGOUT
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("Happy path — Keycloak session invalidated and response confirms logout")
        void logout_success() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(savedEntity));

            AuthResponse response = service.logout(userId);

            verify(identityProvider).logout(keycloakId);

            assertThat(response.message()).contains("logged out");
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.email()).isEqualTo(email);
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.accessToken()).isNull();   // no token in logout response
        }

        @Test
        @DisplayName("Keycloak logout is called with the user's keycloakId, not the app userId")
        void logout_usesKeycloakIdNotAppId() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(savedEntity));

            service.logout(userId);

            verify(identityProvider).logout(keycloakId);
            verify(identityProvider, never()).logout(userId.toString());
        }

        @Test
        @DisplayName("User not found in local DB — throws UserIdentityUnknownException, Keycloak not called")
        void logout_userNotFound_throwsUserIdentityUnknown() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.logout(userId))
                    .isInstanceOf(UserIdentityUnknownException.class);

            verifyNoInteractions(identityProvider);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Builds a fake JWT whose payload contains the given keycloakId as the "sub" claim.
     * Signature verification is intentionally skipped — the service only parses the payload.
     */
    private static String buildJwt(String sub) {
        String payload = "{\"sub\":\"" + sub + "\",\"exp\":9999999999}";
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "fakehdr." + encoded + ".fakesig";
    }
}
