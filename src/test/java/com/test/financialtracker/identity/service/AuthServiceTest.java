package com.test.financialtracker.identity.service;

import com.test.financialtracker.common.exception.ConflictException;
import com.test.financialtracker.identity.domain.entity.Users;
import com.test.financialtracker.identity.domain.models.AuthResponse;
import com.test.financialtracker.identity.domain.models.LoginRequest;
import com.test.financialtracker.identity.domain.models.RegisterRequest;
import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.domain.transformers.UserMapper;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import com.test.financialtracker.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    UserMapper userMapper;
    @Mock IdentityProviderPort  identityProvider;

    @InjectMocks
    AuthService authService;

    // ─────────────────────────────────────────────────────────
    // REGISTRATION TESTS
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Happy path: new user is persisted and confirmation returned")
        void register_newUser_success() {
            RegisterRequest request = new RegisterRequest("jeff@example.com", "Password123");

            when(userRepository.existsByEmail("jeff@example.com")).thenReturn(false);
            when(identityProvider.registerUser(eq("jeff@example.com"), eq("Password123"), eq(User.Role.USER)))
                    .thenReturn("kc-sub-uuid-123");

            // Mapper returns an entity for save()
            Users entity = Users.builder()
                    .id(UUID.randomUUID())
                    .keycloakId("kc-sub-uuid-123")
                    .email("jeff@example.com")
                    .role(User.Role.USER)
                    .build();
            when(userMapper.toEntity(any(User.class))).thenReturn(entity);

            AuthResponse response = authService.register(request);

            // Assertions
            assertThat(response.email()).isEqualTo("jeff@example.com");
            assertThat(response.message()).isEqualTo("Registration successful. Please log in.");
            assertThat(response.accessToken()).isNull();   // No JWT on registration

            // Verify Keycloak was called with USER role
            verify(identityProvider).registerUser("jeff@example.com", "Password123", User.Role.USER);
            verify(userRepository).save(any(Users.class));
        }

        @Test
        @DisplayName("Duplicate email: throws ConflictException before calling Keycloak")
        void register_duplicateEmail_throwsConflict() {
            RegisterRequest request = new RegisterRequest("existing@example.com", "Password123");

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already registered");

            // Keycloak must NOT be called — fail fast at local DB check
            verifyNoInteractions(identityProvider);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Keycloak failure: IdentityProviderException propagates")
        void register_keycloakFails_propagatesException() {
            RegisterRequest request = new RegisterRequest("jeff@example.com", "Password123");

            when(userRepository.existsByEmail("jeff@example.com")).thenReturn(false);
            when(identityProvider.registerUser(any(), any(), any()))
                    .thenThrow(new IdentityProviderPort.IdentityProviderException("Keycloak unreachable", 503));

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IdentityProviderPort.IdentityProviderException.class);

            // Local DB must NOT be written if Keycloak fails
            verify(userRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────
    // LOGIN TESTS
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        // Minimal valid JWT with sub claim for testing
        // Payload (base64): {"sub":"kc-sub-uuid-123","email":"jeff@example.com"}
        private static final String FAKE_JWT =
                "header." +
                        java.util.Base64.getUrlEncoder().withoutPadding()
                                .encodeToString("{\"sub\":\"kc-sub-uuid-123\"}".getBytes()) +
                        ".signature";

        @Test
        @DisplayName("Happy path: returns JWT and user details")
        void login_validCredentials_returnsToken() {
            LoginRequest request = new LoginRequest("jeff@example.com", "Password123");

            when(identityProvider.authenticate("jeff@example.com", "Password123"))
                    .thenReturn(new IdentityProviderPort.TokenResponse(FAKE_JWT, "refresh-token", 300, "Bearer"));

            Users entity = Users.builder()
                    .id(UUID.randomUUID())
                    .keycloakId("kc-sub-uuid-123")
                    .email("jeff@example.com")
                    .role(User.Role.USER)
                    .build();
            when(userRepository.findByKeycloakId("kc-sub-uuid-123")).thenReturn(Optional.of(entity));

            User domainUser = User.builder()
                    .id(entity.getId()).keycloakId("kc-sub-uuid-123")
                    .email("jeff@example.com").role(User.Role.USER).build();
            when(userMapper.toDomain(entity)).thenReturn(domainUser);

            AuthResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo(FAKE_JWT);
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.email()).isEqualTo("jeff@example.com");
        }

        @Test
        @DisplayName("Wrong credentials: IdentityProviderException with 401")
        void login_wrongPassword_throws401() {
            LoginRequest request = new LoginRequest("jeff@example.com", "WrongPassword");

            when(identityProvider.authenticate(any(), any()))
                    .thenThrow(new IdentityProviderPort.IdentityProviderException("Invalid credentials", 401));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(IdentityProviderPort.IdentityProviderException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Orphan identity: Keycloak user has no local profile → ISE")
        void login_noLocalProfile_throwsIllegalState() {
            LoginRequest request = new LoginRequest("ghost@example.com", "Password123");

            when(identityProvider.authenticate(any(), any()))
                    .thenReturn(new IdentityProviderPort.TokenResponse(FAKE_JWT, "refresh", 300, "Bearer"));
            when(userRepository.findByKeycloakId("kc-sub-uuid-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("User profile not found");
        }
    }
}