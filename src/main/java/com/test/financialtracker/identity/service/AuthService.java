package com.test.financialtracker.identity.service;

import com.test.financialtracker.identity.domain.models.AuthResponse;
import com.test.financialtracker.identity.domain.models.LoginRequest;
import com.test.financialtracker.identity.domain.models.RegisterRequest;
import com.test.financialtracker.identity.domain.models.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface AuthService {
    @Transactional
    AuthResponse register(RegisterRequest request, User.Role userRole);

    @Transactional(readOnly = true)
    AuthResponse login(LoginRequest request);

    @Transactional(readOnly = true)
    AuthResponse logout(UUID userId);

}
