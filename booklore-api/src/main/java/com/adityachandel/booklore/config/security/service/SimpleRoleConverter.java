package com.adityachandel.booklore.config.security.service;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SimpleRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Pattern PATTERN = Pattern.compile("[^A-Z0-9_]");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptySet();
        }
        
        Set<String> roles = new HashSet<>();
        
        extractNestedClaim(jwt, new String[]{"realm_access", "roles"}, 0, roles);
        
        extractNestedClaim(jwt, new String[]{"resource_access", "booklore", "roles"}, 0, roles);
        
        addIfPresent(jwt.getClaimAsStringList("groups"), roles);
        addIfPresent(jwt.getClaimAsStringList("roles"), roles);
        
        return roles.stream()
            .map(this::sanitizeAndCreateAuthority)
            .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private void extractNestedClaim(Jwt jwt, String[] path, int index, Set<String> roles) {
        if (index >= path.length) return;
        
        Object claim = jwt.getClaim(path[index]);
        if (claim == null) return;
        
        if (index == path.length - 1) {
            if (claim instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String str) {
                        roles.add(str);
                    }
                }
            }
        } else {
            // Navigate deeper
            if (claim instanceof Map<?, ?> map) {
                Object next = map.get(path[index + 1]);
                if (next instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String str) {
                            roles.add(str);
                        }
                    }
                } else if (next instanceof Map) {
                    // Continue navigating
                    extractNestedClaim(jwt, path, index + 1, roles);
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
        String role = PATTERN.matcher(rawRole.toUpperCase()).replaceAll("_");

        // 3. Ensure prefix
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return new SimpleGrantedAuthority(role);
    }
}
