package com.test.financialtracker.identity.config;

import com.test.financialtracker.identity.ports.IdentityProviderPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Service
public class TokenIntrospectionFilter extends OncePerRequestFilter {

    private final IdentityProviderPort identityProviderPort;

    public TokenIntrospectionFilter(IdentityProviderPort identityProviderPort) {
        this.identityProviderPort = identityProviderPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasLength(bearerToken)) {
            String jwtToken = bearerToken.replace("Bearer ", "");
            if (!this.identityProviderPort.isSessionValid(jwtToken)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        chain.doFilter(request, response);

    }

}
