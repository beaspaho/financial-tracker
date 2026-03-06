package com.test.financialtracker.identity.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class JwtConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public List<GrantedAuthority> convert(Jwt source) {
        Map<String, Object> realmAccess = source.getClaimAsMap("realm_access");
        if (Objects.nonNull(realmAccess)) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (Objects.nonNull(roles)) {
                return roles.stream().map(rn -> new SimpleGrantedAuthority("ROLE_" + rn)).collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
