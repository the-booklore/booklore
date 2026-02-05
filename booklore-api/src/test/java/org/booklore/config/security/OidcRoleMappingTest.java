package org.booklore.config.security;

import org.booklore.config.security.service.SimpleRoleConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcRoleMappingTest {

    private final SimpleRoleConverter converter = new SimpleRoleConverter();

    @Test
    void shouldMapKeycloakRoles() {
        Map<String, Object> realmAccess = Map.of("roles", List.of("admin", "editor"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("realm_access")).thenReturn(realmAccess);
        when(jwt.getClaim("realm_access")).thenReturn(realmAccess);

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EDITOR");
    }

    @Test
    void shouldMapAuthentikGroups() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList("groups")).thenReturn(List.of("super_users"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SUPER_USERS");
    }

    @Test
    void shouldMapSpacesInRoleNames() {
        // Real-world case: "BookLore Admins" from Authentik
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList("groups")).thenReturn(List.of("BookLore Admins", "power-users"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_BOOKLORE_ADMINS", "ROLE_POWER_USERS");
    }
}
