package org.booklore.config.security.service;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SimpleRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";
    private static final Pattern ROLE_SANITIZATION_PATTERN = Pattern.compile("[^A-Z0-9_]");

    @Override
    public Collection<GrantedAuthority> convert(@Nullable Jwt jwt) {
        if (jwt == null) {
            return Collections.emptySet();
        }

        Set<String> roles = new HashSet<>();
        
        // Keycloak realm roles: realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            addRolesFromObject(realmAccess.get(ROLES_CLAIM), roles);
        }
        
        // Keycloak client roles: resource_access.booklore.roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            Object clientAccess = resourceAccess.get("booklore");
            if (clientAccess instanceof Map<?, ?> clientMap) {
                addRolesFromObject(clientMap.get(ROLES_CLAIM), roles);
            }
        }
        
        // Authentik/generic groups and roles claims (top-level)
        addIfPresent(jwt.getClaimAsStringList("groups"), roles);
        addIfPresent(jwt.getClaimAsStringList(ROLES_CLAIM), roles);

        return roles.stream()
            .map(this::sanitizeAndCreateAuthority)
            .collect(Collectors.toSet());
    }

    private void addRolesFromObject(Object rolesObj, Set<String> roles) {
        if (rolesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String str) {
                    roles.add(str);
                }
            }
        } else if (rolesObj instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof String str) {
                    roles.add(str);
                }
            }
        }
    }

    private void addIfPresent(List<String> claims, Set<String> roles) {
        if (claims != null) {
            roles.addAll(claims);
        }
    }

    private SimpleGrantedAuthority sanitizeAndCreateAuthority(String rawRole) {
        // 1. Upper case
        // 2. Replace spaces/dashes with underscores (Fixes "BookLore Admins")
        String role = ROLE_SANITIZATION_PATTERN.matcher(rawRole.toUpperCase()).replaceAll("_");

        // 3. Ensure prefix
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return new SimpleGrantedAuthority(role);
    }
}
