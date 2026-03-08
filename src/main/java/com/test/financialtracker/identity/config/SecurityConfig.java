package com.test.financialtracker.identity.config;

import com.test.financialtracker.identity.domain.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity               // enables @PreAuthorize on service methods
@RequiredArgsConstructor
public class SecurityConfig  {

    private final RateLimitingFilter rateLimitingFilter;
    private final TokenIntrospectionFilter tokenIntrospectionFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                                .permitAll().requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

                .requestMatchers("/api/v1/admin/**").hasRole(User.Role.ADMIN.name())

                .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))

                // Rate limiting runs before anything
                .addFilterBefore(rateLimitingFilter, DisableEncodeUrlFilter.class)
                .addFilterAfter(tokenIntrospectionFilter, RateLimitingFilter.class)
        ;

        return http.build();
    }

    /**
     * Extracts roles from Keycloak's JWT structure:
     * <p>
     * <p>
     * Spring Security expects "ROLE_USER" / "ROLE_ADMIN" as granted authorities.
     * This converter reads realm_access.roles and adds the ROLE_ prefix.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtConverter());
        return converter;
    }

}
